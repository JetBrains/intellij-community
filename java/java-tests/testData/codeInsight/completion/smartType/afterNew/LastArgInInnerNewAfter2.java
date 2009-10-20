class B {
    private static int getSeed() { return 0; }
    class A {
      A(int i) {}
    }
    public static void main(String[] args) {
      new A(getSeed()).show();
    }
}