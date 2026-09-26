// "Assert 'obj instanceof String'" "true-preview"
class X {
  void test(Object obj) {
    if (obj instanceof Integer) System.out.println();
    String string = (<caret>String)obj;
  }
}