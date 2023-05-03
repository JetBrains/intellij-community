class Test {

  public void one() {
    long[] x = new long[]{0,0};
    two(x);
    two(new long[]{0, 0});
  }

  public void two(long[] values) {}
}