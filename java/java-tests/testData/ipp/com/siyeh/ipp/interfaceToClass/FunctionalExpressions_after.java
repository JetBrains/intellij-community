abstract class <caret>FunctionalExpressions {
  public abstract void foo();
}

class Test {
  {
    FunctionalExpressions fe = () -> {};
  }
}