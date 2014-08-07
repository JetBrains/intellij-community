// "Convert to ThreadLocal" "true"
class Test {
  Integer <caret>field=new Integer(0);
  void foo() {
    if (field == null) return;
  }
}