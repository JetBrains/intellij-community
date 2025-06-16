interface ITest {
  default void bar() {
  }
}

public class Test implements ITest {
  @Override
  public void bar() {
  }
}
