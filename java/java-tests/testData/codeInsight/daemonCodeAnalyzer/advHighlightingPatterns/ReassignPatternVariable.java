class X {
  void expressions(Object obj) {
    if (obj instanceof String s) {
      s = "foo";
    }
    if (obj instanceof final String s) {
      <error descr="Cannot assign a value to final variable 's'">s</error> = "foo";
    }
  }
}