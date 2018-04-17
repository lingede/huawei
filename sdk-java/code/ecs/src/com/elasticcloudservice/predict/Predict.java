package com.elasticcloudservice.predict;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

class Vmformat{
	public String name;
	public  int cpu;
	public int mem;//单位gb

	public Vmformat() {
	}

	public Vmformat(String name, int cpu, int mem) {
		this.name = name;
		this.cpu = cpu;
		this.mem = mem;
	}

	@Override
	public String toString() {
		return "Vmformat{" +
				"name='" + name + '\'' +
				", cpu=" + cpu +
				", mem=" + mem +
				'}';
	}
}

class Data{
	String vmId;
	String vmName;
	Date time;

	public Data() {
	}

	public Data(String vmId, String vmName, Date time) {
		this.vmId = vmId;
		this.vmName = vmName;
		this.time = time;
	}

	@Override
	public String toString() {
		return "Data{" +
				"vmId='" + vmId + '\'' +
				", vmName='" + vmName + '\'' +
				", time=" + time +
				'}';
	}
}
class HardServer{
	private static int counter=1;
	final int id=counter++;
	HashMap<Integer,Integer> vmnum;
	int cpu;
	int mem;
	HardServer(int n){
		cpu=0;
		mem=0;
		vmnum=new HashMap<Integer, Integer>();
		for(int i=0;i<n;i++){
			vmnum.put(i,0);
		}
	}

	@Override
	public String toString() {
		return "HardServer{" +
				"id=" + id +
				", vmnum=" + vmnum +
				", cpu=" + cpu +
				", mem=" + mem +
				'}';
	}
}
public class Predict {

	public static String[] predictVm(String[] escContent, String[] inputContent) {

		int ecsindex=0,inputindex=0;
		//cpu内存硬盘大小
		int cpu,mem,disk;
		String s=inputContent[ecsindex++];
		String a[]=s.split(" ");
		cpu=Integer.parseInt(a[0]);
		mem=Integer.parseInt(a[1]);
		disk=Integer.parseInt(a[2]);
		ecsindex++;
		//虚拟机规格数量
		int vmnum=Integer.parseInt(inputContent[ecsindex++]);
		Vmformat vmformat[]=new Vmformat[vmnum];
		for(int i=0;i<vmnum;i++){
			s=inputContent[ecsindex++];
			a=s.split(" ");
			vmformat[i]=new Vmformat(a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2])/1024);
		}
		ecsindex++;
		//需要优化的资源维度名称
		String dimension=inputContent[ecsindex++];
		ecsindex++;

		//预测的开始时间
		Date forecastStart=new Date();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		try{
			forecastStart=simpleDateFormat.parse(inputContent[ecsindex++]);}
		catch (ParseException e){
			e.printStackTrace();
		}

		//预测的结束时间
		Date forecastEnd=new Date();
		try{
			forecastEnd=simpleDateFormat.parse(inputContent[ecsindex++]);}
		catch (ParseException e){
			e.printStackTrace();
		}

		//读取过去数据
		List<Data> dataList=new ArrayList<Data>();

		for(int i=0;i<escContent.length;i++){
			a=escContent[i].split("\\s+");
			Date temp=new Date();
			try{
				temp=simpleDateFormat.parse(a[2]+" "+a[3]);}
			catch (ParseException e){
				e.printStackTrace();
			}
			dataList.add(new Data(a[0],a[1],temp));
		}

		//预测的天数
		int forcastday=(int)(Math.abs((forecastEnd.getTime()-forecastStart.getTime())/86400000));

		//训练的天数
		int practiceday=(int)(Math.abs((dataList.get(dataList.size()-1).time.getTime()-dataList.get(0).time.getTime())/86400000)+1);
		//每天训练的数量
		int practicenum[][]=new int[practiceday][vmnum];
		for(int i=0;i<practiceday;i++)
			for(int j=0;j<vmnum;j++)
				practicenum[i][j]=0;
		Calendar calendar1 = Calendar.getInstance();
		calendar1.setTime(dataList.get(0).time);
		calendar1.set(calendar1.get(Calendar.YEAR), calendar1.get(Calendar.MONTH), calendar1.get(Calendar.DAY_OF_MONTH),
				0, 0, 0);
		Date beginOfDate = calendar1.getTime();

		for(Data d:dataList){
			int days=(int)((d.time.getTime()-beginOfDate.getTime())/86400000);
			for(int i=0;i<vmnum;i++){
				if(vmformat[i].name.equals(d.vmName)){
					practicenum[days][i]++;
				}
			}
		}


		//预测的虚拟机总数
		int forcastvmnum[]=new int[vmnum];
		/*for(int j=0;j<vmnum;j++){
			double temp=0;
			for(int i=practiceday-forcastday;i<practiceday;i++){
				temp=temp+practicenum[i][j];
			}
			forcastvmnum[j]=(int)Math.ceil(temp);
		}*/
		predictFlavorsByAvg(practicenum,forcastday,forcastvmnum);

		//物理服务器
		List<HardServer> hardServers=new ArrayList<HardServer>();
		//cpu权重复制
		int w1[]=new int[vmnum+1];
		w1[0]=0;
		for(int i=1;i<=vmnum;i++)
			w1[i]=vmformat[i-1].cpu;
       //mem权重复制
		int w2[]=new int[vmnum+1];
		w2[0]=0;
		for(int i=1;i<=vmnum;i++)
			w2[i]=vmformat[i-1].mem;
		//根据输入设置背包价值为cpu或mem
		int v[]=new int[vmnum+1];
		v[0]=0;
		if(dimension.equals("CPU")) {
			for (int i = 1; i <= vmnum; i++)
				v[i] = vmformat[i-1].cpu;
		}else{
			for (int i = 1; i <= vmnum; i++)
				v[i] = vmformat[i-1].mem;
		}
		//复制预测虚拟机数量
		int num[]=new int[vmnum+1];
		num[0]=0;
		for (int i = 1; i <= vmnum; i++)
			num[i] = forcastvmnum[i-1];
		//一次背包中装每种虚拟机的数量
		int res[]=new int[vmnum+1];
		int index=0;
		while(!isZero(num)){
			for(int i=0;i<=vmnum;i++)
				res[i]=0;
			hardServers.add(new HardServer(vmnum));
			HardServer hardServer=hardServers.get(index);
			int value=knapsack(w1,w2,v,num,res,vmnum,cpu,mem);
			for(int i=0;i<vmnum;i++){
				hardServer.vmnum.put(i,res[i+1]);
				num[i+1]=num[i+1]-res[i+1];
				hardServer.cpu+=res[i+1]*vmformat[i].cpu;
				hardServer.mem+=res[i+1]*vmformat[i].mem;
			}
			index++;
		}

		List<String> list=new ArrayList<>();
		int vmsum=0;
		for(int i=0;i<vmnum;i++){
			vmsum+=forcastvmnum[i];
		}
		list.add(vmsum+"");
		for(int i=0;i<vmnum;i++){
			list.add(vmformat[i].name+" "+forcastvmnum[i]);
		}
        list.add("");
		list.add(hardServers.size()+"");
		for(HardServer h:hardServers){
			StringBuffer stringBuffer=new StringBuffer();
			stringBuffer.append(h.id+" ");
			for(int k:h.vmnum.keySet()){
				stringBuffer.append(vmformat[k].name+" "+h.vmnum.get(k)+" ");
			}
			list.add(stringBuffer.toString());
		}
		String[] results= new String[list.size()];
		for(int i=0;i<list.size();i++){
			results[i]=list.get(i);
		}
		int testValue = 5;
		return results;
	}
	static boolean isZero(int a[]){
		int n=a.length;
		for(int i=0;i<n;i++){
			if(a[i]!=0){
				return false;
			}
		}
		return true;
	}
    //多重二维费用背包问题
	static int knapsack(int w1[],int w2[],int v[],int num[],int res[],int n,int c1,int c2){
		int value=0;
		int f[][][]=new int[n+1][c1+1][c2+1];
		for(int i=0;i<=n;i++)
			for(int j=0;j<=c1;j++)
				for(int k=0;k<c2;k++)
					f[i][j][k]=0;

		for(int i=1;i<=n;i++)
			for(int j=1;j<=c1;j++)
				for(int k=1;k<=c2;k++)
					if(j>=w1[i]&&k>=w2[i]){
						int count1=Math.min(num[i],j/w1[i]);
						int count2=Math.min(num[i],k/w2[i]);
						int count=Math.min(count1,count2);
						f[i][j][k]=f[i-1][j][k];
						for(int t=1;t<=count;t++) {
							int temp=f[i-1][j-w1[i]*t][k-w2[i]*t]+t*v[i];
							if(temp>f[i][j][k])
								f[i][j][k]=temp;
						}
					}else{
						f[i][j][k]=f[i-1][j][k];
					}




		value=f[n][c1][c2];
		int z=c2;
		int y=c1;
		int x=n;
		while(x!=0){
			int count1=Math.min(num[x],y/w1[x]);
			int count2=Math.min(num[x],z/w2[x]);
			int count=Math.min(count1,count2);
			for(int k=count;k>0;k--){
				if(f[x][y][z]==(f[x-1][y-k*w1[x]][z-k*w2[x]]+k*v[x])){
					res[x]=k;
					y=y-k*w1[x];
					z=z-k*w2[x];
					break;
				}
			}
			x--;
		}


		return value;
	}

	//通过平均数预测
	static void predictFlavorsByAvg(int[][] practiceData,int forcastday,int[] forcastvmnum){
		int practiceDays = practiceData.length;
		int vmCounts = practiceData[0].length;
		//求总和
		for(int day = 0;day < practiceDays;day++){
				for(int vmIndex = 0;vmIndex < vmCounts;vmIndex++){
					forcastvmnum[vmIndex] += practiceData[day][vmCounts];
				}
		}

		for(int vmIndex = 0;vmIndex < vmCounts;vmIndex++){
			forcastvmnum[vmIndex] = (int)Math.ceil(forcastvmnum[vmIndex] / Double.parseDouble(String.valueOf(practiceDays)) * forcastday)  + 1;
		}

	}

}
