public class Test<T> {
  public Test(T t){}
  void foo(){}
  public static void main(String[] args){
    new Test<>(args[0]).foo();
  }
}