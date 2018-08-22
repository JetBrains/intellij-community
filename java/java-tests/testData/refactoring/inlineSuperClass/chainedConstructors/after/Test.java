class Test {
    int i;

    public Test() {
        this(1);
    }

    Test(int i, int j) {
      this.i = i;
    }

    Test(int i) {
      this(i, 0);
    }
}