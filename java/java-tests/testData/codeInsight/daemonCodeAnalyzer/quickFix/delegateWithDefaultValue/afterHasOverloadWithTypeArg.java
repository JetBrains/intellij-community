// "Generate overloaded method with default parameter values" "true"
class Test {
  void method(String... s) {}
  
  <T> void <caret>method() {}
}