class T {
  boolean ne(Object a, Object b) {
    return a == null ? b != null : !a.<caret>equals(b);
  }
}