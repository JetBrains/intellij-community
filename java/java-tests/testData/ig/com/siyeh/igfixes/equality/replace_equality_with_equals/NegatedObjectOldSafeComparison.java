public class NegatedObjectOldSafeComparison {
  boolean a(Object a, Object b) {
    return a !=<caret> b;
  }
}