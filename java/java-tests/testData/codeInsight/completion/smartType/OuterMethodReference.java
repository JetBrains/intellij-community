class Outer {
  void foo(){}

  class Inner {
    void bar() {
      Runnable r = fo<caret>
    }
  }
}