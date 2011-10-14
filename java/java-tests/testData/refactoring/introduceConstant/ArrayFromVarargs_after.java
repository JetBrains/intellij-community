class B1 {
  public B1(int i, String... s) {}
}
class A1 extends B1 {
    public static final String[] xxx = new String[]{"a", "b"};

    A1() {
    super(1, xxx);
  }
}