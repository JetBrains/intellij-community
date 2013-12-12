class Test {
  interface I {
    void foo();
  }

  {
    I i = Unknown::<caret>
  }
}