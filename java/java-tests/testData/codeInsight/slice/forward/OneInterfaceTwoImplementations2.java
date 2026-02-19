interface JavaInterface {
  void foo(Object <caret>p);
}

class JavaClass1 implements JavaInterface {
  @Override
  public void foo(Object p) {
    System.out.println(<flown1>p);
  }
}

class JavaClass2 implements JavaInterface {
  @Override
  public void foo(Object p) {
    System.err.println(<flown2>p);
  }
}