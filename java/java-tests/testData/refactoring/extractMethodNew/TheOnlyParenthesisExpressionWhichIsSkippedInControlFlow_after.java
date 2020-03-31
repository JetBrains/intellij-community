class Test {
  {
    int i = 2;
    int n = 5;
    boolean b = !newMethod(i, n);
  }

    private boolean newMethod(int i, int n) {
        return i < n;
    }
}
