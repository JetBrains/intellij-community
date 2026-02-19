class Test {
    String f;
    void foo(String... s) {
      for (String s1 : s) {

      }
    }

    void bar() {
      foo(f);
    }
}
