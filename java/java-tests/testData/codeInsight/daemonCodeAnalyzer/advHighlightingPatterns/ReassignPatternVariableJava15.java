class X {
  void expressions(Object obj) {
    if (obj instanceof String s) {
      <error descr="Cannot assign a value to final variable 's'">s</error> = "foo";
    }
    if (obj instanceof <error descr="Modifier 'final' not allowed here">final</error> String s) {
      <error descr="Cannot assign a value to final variable 's'">s</error> = "foo";
    }
  }
}