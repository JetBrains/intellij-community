class Test {
  int i;
  int getI() {
    return i;
  }

  void foo() {
    setI(getI() + 1);
    System.out.println(getI());

    Test t;
    setI(t.getI());
  }

    public void setI(int i) {
        this.i = i;
    }
}
