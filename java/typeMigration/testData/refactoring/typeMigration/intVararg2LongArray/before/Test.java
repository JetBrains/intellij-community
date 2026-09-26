class Test {

  public void one() {
    int[] x = new int[]{0,0};
    two(x);
    two(0, 0);
  }

  public void two(int... values) {}
}