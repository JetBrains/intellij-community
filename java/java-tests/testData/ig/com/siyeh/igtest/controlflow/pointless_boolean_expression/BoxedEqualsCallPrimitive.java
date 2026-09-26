class Boxed {
  String method() {
    if (<warning descr="'Boolean.TRUE.equals(returnsBool())' can be simplified to 'returnsBool()'">Boolean.TRUE.equals(returnsBool())</warning>) {
      return "foo";
    }
    return "baz";
  }

  public boolean returnsBool() {
    return Math.random() > 0.5 ? true : false;
  }
}
