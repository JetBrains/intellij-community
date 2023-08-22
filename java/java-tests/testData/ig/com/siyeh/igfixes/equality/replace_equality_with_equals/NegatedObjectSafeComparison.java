public class NegatedObjectSafeComparison {

  boolean a(Object a, Object b) {
    return a !=<caret> b;
  }
}