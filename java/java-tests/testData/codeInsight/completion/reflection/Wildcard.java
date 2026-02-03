class Main {
  void foo() throws NoSuchMethodException {
    clazz().getMethod("<caret>");
  }
  private Class<? extends Test> clazz() {return Test.class;}
}

class Test {
  public void method(){}
}