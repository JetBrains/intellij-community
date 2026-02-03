interface ITest {
  void foo();

  default void bar() {
  }
}

public class Test implements ITest {
  ITest lambda = () -> ITest.super.bar();

  @Override
  public void foo() {
  }
}