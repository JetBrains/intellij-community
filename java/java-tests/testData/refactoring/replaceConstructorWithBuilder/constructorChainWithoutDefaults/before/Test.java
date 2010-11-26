public class Test {
  public Test(int i, int j){}
  public Test(int j){
    this(2, j);
  }
  void foo(){}
  public static void main(String[] args){
    new Test(1).foo();
    new Test(2, 3).foo();
  }
}