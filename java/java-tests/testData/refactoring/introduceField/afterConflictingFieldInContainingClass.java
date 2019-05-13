class A {
  private final int aField = 1;

  class B {

      public final int aField = 2;

      int method() {
      return A.this.aField + aField;
    }
  }
}
