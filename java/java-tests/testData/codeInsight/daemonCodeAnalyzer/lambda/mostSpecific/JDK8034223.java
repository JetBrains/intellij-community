class MS {
  interface GetInt { int get(); }
  interface GetInteger { Integer get(); }

  private void m(GetInt getter) {
    System.out.println(getter);
  }

  private void m(GetInteger getter) {
    System.out.println(getter);
  }

  void test(boolean cond) {
    m(cond ? () -> 26 : () -> 24);
    m<error descr="Cannot resolve method 'm(?)'">(cond ? () -> 26 : () -> new Integer(42))</error>;
    m(cond ? () -> new Integer(26) : () -> new Integer(42));
  }
}
