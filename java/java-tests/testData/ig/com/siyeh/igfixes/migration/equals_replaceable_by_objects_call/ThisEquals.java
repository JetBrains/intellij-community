class T {
  String s;
  boolean same(String s) {
    return this.s.<caret>equals(s);
  }
}