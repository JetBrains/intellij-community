class Fun {
  private void parseDeclarator(Object builder, boolean isTuple) {
    if (!isTuple) {
      return;
    }
    else {
      if (smth() && <warning descr="Condition 'isTuple' is always 'true' when reached">isTuple</warning>) {
        System.out.println();
      }
    }
  }

  boolean smth() { return true; }
}