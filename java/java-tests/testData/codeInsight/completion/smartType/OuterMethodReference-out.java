class Outer {
  void foo(){}

  class Inner {
    void bar() {
      Runnable r = Outer.this::foo;<caret>
    }
  }
}