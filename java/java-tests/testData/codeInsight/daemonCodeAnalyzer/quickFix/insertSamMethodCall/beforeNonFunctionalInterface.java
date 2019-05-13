// "Insert '.doSomething' to call functional interface method" "false"
public class Test {
  interface MyFn {
    void doSomething(String s, int i);
    void doSomething();
  }

  public void test(MyFn fn) {
    fn(<caret>);
  }
}