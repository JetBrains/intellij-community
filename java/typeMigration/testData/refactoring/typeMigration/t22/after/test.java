class Test {
    Integer f;
    void foo(Integer... s) {
      for (Integer s1 : s) {

      }
    }

    void bar() {
      foo(f);
    }
}
