class Contracts {

  void test(boolean b, boolean c) {
    boolean x = false;
    <warning descr="Condition 'x' at the left side of assignment expression is always 'false'. Can be simplified">x</warning> |= b && c;
    System.out.println(x);
  }

  private boolean method(boolean a) {
      boolean b = true;
      <warning descr="Condition 'b' at the left side of assignment expression is always 'true'. Can be simplified"><caret>b</warning> |= a;
      return b;
  }

}