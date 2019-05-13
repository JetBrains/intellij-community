// "Convert to ThreadLocal" "true"
class Test {
  Integer <caret>field=new Integer(0);
  void foo(Test t) {
    if (t.field == null) return;
  }
}