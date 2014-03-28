class Contracts {

  private boolean method(boolean a) {
      boolean b = true;
      <warning descr="Condition 'b' at the left side of assignment expression is always 'true'. Can be simplified"><caret>b</warning> |= a;
      return b;
  }

}