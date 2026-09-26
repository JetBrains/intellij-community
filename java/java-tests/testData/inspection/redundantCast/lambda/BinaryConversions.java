class Test {
  void m(Object a) {
    if ((Boolean)a && (Boolean)a) { }
  }

  Object concat(Object x, Object y) {
    return x + (String) y;
  }
}