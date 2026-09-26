class X {
  void foo(java.util.List l) {
    for (Object o : l) {
      <selection>if (o == null) continue;
      Object x = bar(o);</selection>
      System.out.println(x);
    }
  }

  private String bar(Object o) {
    return "";
  }
}
