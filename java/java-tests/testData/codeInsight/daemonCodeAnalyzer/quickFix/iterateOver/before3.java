// "Iterate" "false"
class Test {
  void foo() {
    final Annotation[] annotations = getClass().getAnnotat<caret>ions();
  }
}