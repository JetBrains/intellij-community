// "Surround with 'if (obj instanceof String)'" "true"
class X {
  void test(Object obj) {
    if (obj instanceof Integer) System.out.println();
    String string = (<caret>String)obj;
  }
}