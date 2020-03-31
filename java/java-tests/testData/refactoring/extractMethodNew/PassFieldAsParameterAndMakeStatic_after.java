class X {
  private int myI;
  void foo() {
      newMethod(this.myI);
  }

    private static void newMethod(int myI) {
        int i = myI;
    }
}
