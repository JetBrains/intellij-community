// "Convert to atomic" "true-preview"
class Test {
  Object[] <caret>field=foo();
  Object[] foo() {
    return null;
  }
}