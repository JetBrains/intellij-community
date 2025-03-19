class T {
  boolean eq(Object a, Object b) {
    return a == null ? b == null : a.<caret>equals(b);
  }
}