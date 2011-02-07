interface I {
  boolean foo(Object obj);

  boolean foo(Integer myObj);
}

abstract class C implements I {
  public boolean foo(Object obj) {
    return <ref>foo((Integer)obj);
  }
}
