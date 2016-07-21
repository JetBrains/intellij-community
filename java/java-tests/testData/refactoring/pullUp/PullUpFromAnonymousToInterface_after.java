class Test {
  {
    I i = new I() {
      @Override
      public void foo() {}
    };
  }

  interface I {
      void foo();
  }
}