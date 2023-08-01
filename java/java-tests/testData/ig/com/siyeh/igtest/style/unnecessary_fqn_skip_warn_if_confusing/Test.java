class Test {

  void m(Object value) {
    value = new java.sql.Date(((java.<caret>util.Date) value).getTime());
  }
}