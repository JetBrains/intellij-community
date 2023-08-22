interface <caret>FunctionalExpressions {
  void foo();
}

class Test {
  {
    FunctionalExpressions fe = () -> {};
  }
}