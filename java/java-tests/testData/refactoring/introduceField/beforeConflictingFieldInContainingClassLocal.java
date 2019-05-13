class A {
  private final int aField = 1;

  class B {

    int method() {
      int <selection>i</selection> = 2;
      return aField + i;
    }
  }
}
