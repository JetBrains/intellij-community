public class SimpleObjectOldSafeComparison {
  boolean eq(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }
}