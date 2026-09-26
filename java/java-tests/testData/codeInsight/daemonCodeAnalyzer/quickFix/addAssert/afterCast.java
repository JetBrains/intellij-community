// "Assert 'obj instanceof String'" "true-preview"
class X {
  void test(Object obj) {
    if (obj instanceof Integer) System.out.println();
      assert obj instanceof String;
      String string = (String)obj;
  }
}