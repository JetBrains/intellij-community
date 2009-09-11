public class Test {
  String str;
  public Test(int i){}
  public Test(){
    this(2);
  }
  public Test(String s){
    this(3);
    str = s;
  }
  void foo(){}
  public static void main(String[] args){
    new Test(1).foo();
  }
}