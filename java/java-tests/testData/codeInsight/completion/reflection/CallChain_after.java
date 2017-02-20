class Main {
  void foo() throws ReflectiveOperationException {
    bar().getField("num");
  }

  Class<Test> bar() throws ClassNotFoundException { return (Class<Test>) Class.forName("Test"); }
}

class Test {
  public int num;
  public void method(){}
}
