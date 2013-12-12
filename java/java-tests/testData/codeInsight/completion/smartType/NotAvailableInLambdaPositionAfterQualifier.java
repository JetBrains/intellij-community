class Test {
  interface I {
    void foo();
  }

  {
    I i = Test::<caret>
  }
}