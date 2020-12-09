// "Replace with 'new Object[]{getObject(/*empty*/)}'" "true"

class Test {
  static void foo(Object... data) { }

  void test(boolean b, Object[] obj2) {
    foo(b ? getObject(/*empty*/<caret>) : obj2);
  }

  native Object getObject();
}