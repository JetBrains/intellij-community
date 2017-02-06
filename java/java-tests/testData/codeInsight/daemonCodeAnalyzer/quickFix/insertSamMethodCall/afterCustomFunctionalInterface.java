// "Insert '.doSomething' to call functional interface method" "true"
public class Test {
  interface MyFn {
    void doSomething(String s, int i);
  }

  public void test(MyFn fn) {
    fn.doSomething();
  }
}