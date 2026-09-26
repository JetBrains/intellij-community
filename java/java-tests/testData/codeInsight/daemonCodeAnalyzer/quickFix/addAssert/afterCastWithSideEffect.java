// "Introduce variable and assert 'o instanceof String'" "true-preview"
class X {
  native Object getObject();

  void test(boolean b) {
      Object o = b ? Integer.valueOf(1) : getObject();
      assert o instanceof String;
      String string = (String) o;
  }
}
