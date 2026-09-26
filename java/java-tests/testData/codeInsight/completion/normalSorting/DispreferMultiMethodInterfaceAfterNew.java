class Test {
  Intf i = new <caret>
}

interface Intf {
  void foo();
  void bar();
  void goo();
}

class IntfImpl implements Intf {
  public void foo() {}
  public void bar() {}
  public void goo() {}
}