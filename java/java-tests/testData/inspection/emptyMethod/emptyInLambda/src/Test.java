interface ITest {
  int foo();
}

public class Test implements ITest {
  ITest lambda = () -> {};

  @Override
  public int foo() {
  }

  public int bar() {
    return 1;
  }
}

class Sub extends Test {
  ITest lambda1 = () -> super.foo();
  ITest lambda2 = () -> { return super.foo(); };
}