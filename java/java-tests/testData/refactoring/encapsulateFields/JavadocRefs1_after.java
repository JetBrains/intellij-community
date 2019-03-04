class B {
  public int getI() {

    return 0;
  }

  static class A {

    private int i = 0;

    /**
     * {@link B#getI()}
     */
    void f() {}

      public int getI() {
          return i;
      }

      public void setI(int i) {
          this.i = i;
      }
  }
}