public class Test {
  public Test(int j, int... i){}
  void foo(){}
  public static void main(String[] args){
    new Test(1, 2, 3).foo();
  }
}