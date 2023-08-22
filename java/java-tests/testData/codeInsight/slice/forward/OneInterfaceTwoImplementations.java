interface JavaInterface {
  void foo(Object p);
}

class JavaClass1 implements JavaInterface {
  @Override
  public void foo(Object <caret>p) {
    System.out.println(<flown1>p);
  }
}

class JavaClass2 implements JavaInterface {
  @Override
  public void foo(Object p) {
    System.err.println(p);
  }
}