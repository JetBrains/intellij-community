public class Test {
  public Test(int i){}
  public Test(){
    this(2);
  }
  void foo(){}
  public static void main(String[] args){
    new Builder().setI(1).createTest().foo();
  }
}