class A {
  boolean foo() {

    <selection>for (int i = 0; i < 9; i++) {
      final Boolean foo = Boolean.FALSE;
      if (foo != null) {
        return foo;
      }
    }</selection>


    return false;
  }
}