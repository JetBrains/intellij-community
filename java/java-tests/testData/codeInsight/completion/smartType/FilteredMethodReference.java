class Test {
  interface I {
    int foo();
  }

  static int aa() {
    return 0;
  }

  {
    I i = Test::<caret>
  }
}