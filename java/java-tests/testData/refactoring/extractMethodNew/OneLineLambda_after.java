class Test {
  public void foo() {
    Comparable<String> c = o -> newMethod(o);
  }

    private int newMethod(String o) {
        return o.indexOf("foo");
    }
}
