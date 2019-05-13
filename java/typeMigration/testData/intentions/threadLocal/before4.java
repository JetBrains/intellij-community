// "Convert to ThreadLocal" "true"
class Test {
  String <caret>field="";
  void foo() {
    if (field.indexOf("a") == -1) return;
  }
}