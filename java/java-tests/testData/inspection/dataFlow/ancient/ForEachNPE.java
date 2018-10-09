class Test {
  public String foo(String[] path) {
    if (path != null) return null;
    for (String p: <warning descr="Dereference of 'path' may produce 'NullPointerException'">path</warning>) {}

    return "";
  }
}
