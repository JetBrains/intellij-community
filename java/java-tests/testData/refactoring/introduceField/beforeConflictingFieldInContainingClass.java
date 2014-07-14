class A {
  private final int aField = 1;

  class B {

    int method() {
      return aField + <selection>2</selection>;
    }
  }
}
