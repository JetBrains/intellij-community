class Test {
    String f;
    void foo(String... s) {}

    void bar() {
      foo(f);
    }
}
