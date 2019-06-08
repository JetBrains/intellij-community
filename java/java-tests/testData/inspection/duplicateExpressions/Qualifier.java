class A {
  int k;

  void testThis() {
    int i = this.k;
    int j = this.k;
  }

  static class B extends A {
    void testSuper() {
      int i = super.k;
      int j = super.k;
    }
  }
}