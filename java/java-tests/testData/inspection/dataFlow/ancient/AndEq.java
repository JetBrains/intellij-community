class Test {
  public void foo(boolean x, boolean y, boolean z) {
    boolean r = true;
    <warning descr="Condition 'r' at the left side of assignment expression is always 'true'. Can be simplified">r</warning> &= x;
    r &= y;
    r &= z;
  }
}