// "Convert to atomic" "true"
class Test {
  Object[] <caret>field=foo();
  Object[] foo() {
    return null;
  }
}