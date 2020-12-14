// "Replace with 'new Object[]{getObject(/*empty*/)}'" "true"

class Test {
  static void foo(Object... data) { }

  void test(boolean b, Object[] obj2) {
    foo(b ? new Object[]{getObject(/*empty*/)} : obj2);
  }

  native Object getObject();
}