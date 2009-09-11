public class Test {
  public Test(int... i){}
  void foo(){}
  public static void main(String[] args){
    new Builder().setI(1, 2, 3).createTest().foo();
  }
}