// "Convert to atomic" "true"
class Test {
  static final String <caret>field="br";
  {
    new Test(field);
  }

  Test(String field) { }
}