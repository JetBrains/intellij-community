// "Introduce variable and assert 'o instanceof String'" "true-preview"
class X {
  native Object getObject();

  void test(boolean b) {
    String string = (<caret>String)(b ? Integer.valueOf(1) : getObject());
  }
}
