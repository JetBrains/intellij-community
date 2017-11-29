class Test {
  void foo() {
    @Deprecated(forRemoval = true)
    class Local {
      @Deprecated(forRemoval = true)
      int bar;
      @Deprecated(forRemoval = true)
      void bar() {}
    }

    Local local = new Local();
    int i = local.bar;
    local.bar();
  }
}
