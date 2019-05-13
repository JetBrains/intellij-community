class Contracts {

  void test(boolean b, boolean c) {
    boolean x = false;
    x |= b && c;
    System.out.println(x);
  }

  private boolean method(boolean a) {
      boolean b = true;
      return b;
  }

}