public class Test {
  public Test(int i, int j){}
  public Test(int j){
    this(2, j);
  }
  void foo(){}
  public static void main(String[] args){
    new Builder().setJ(1).createTest().foo();
    new Builder().setI(2).setJ(3).createTest().foo();
  }
}