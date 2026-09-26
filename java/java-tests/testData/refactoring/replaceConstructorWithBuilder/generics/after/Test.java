public class Test<T> {
  public Test(T t){}
  void foo(){}
  public static void main(String[] args){
    new Builder<String>().setT(args[0]).createTest().foo();
  }
}