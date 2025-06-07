class Boxed {
  String method() {
    if (<warning descr="'returnsBool() == Boolean.TRUE' can be simplified to 'returnsBool()'">returnsBool() == Boolean.TRUE</warning>) {
      return "foo";
    }
    return "baz";
  }

  public boolean returnsBool() {
    return Math.random() > 0.5 ? true : false;
  }
}
