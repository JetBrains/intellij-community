class Test {
  interface A {
    void foo();
  }
  interface B {}

  void test(Object obj) {
    boolean isA = obj instanceof A;
    if (!isA && !(obj instanceof B)) {
      return;
    }
    if (isA) {
      ((A) obj).foo();<caret>
    }
  }
}