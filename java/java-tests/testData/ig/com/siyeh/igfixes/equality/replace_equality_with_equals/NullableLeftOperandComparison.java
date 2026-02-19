public class NullableLeftOperandComparison {

  boolean a(Object a, Object b) {
    if (a != null) return false;
    return a ==<caret> b;
  }
}