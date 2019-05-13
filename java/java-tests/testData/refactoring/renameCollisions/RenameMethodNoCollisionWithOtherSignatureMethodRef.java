class Test {

  static void f<caret>oo() {}
  static void foo2(int i) {}

  {
    Runnable r = Test :: foo;
  }
}