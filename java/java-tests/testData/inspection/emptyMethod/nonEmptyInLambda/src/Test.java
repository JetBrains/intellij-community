interface ITest {
  void foo();

  default void bar() {
  }
}

public class Test implements ITest {
  ITest lambda = () -> super.bar();

  @Override
  public void foo() {
  }
}
