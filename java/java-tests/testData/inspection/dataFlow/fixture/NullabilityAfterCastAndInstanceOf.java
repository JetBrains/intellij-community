class Test {
  class X {}

  native Object getObject();
  native X getX();

  public void check(int val, boolean b) {
    Object typ = b ? getObject() : getX();

    if (val == 1) {
      System.out.println((X)typ);
    }
    else if (val == 2) {
      if (typ instanceof X) {
      }
    }

    System.out.println(typ.hashCode());
  }
}