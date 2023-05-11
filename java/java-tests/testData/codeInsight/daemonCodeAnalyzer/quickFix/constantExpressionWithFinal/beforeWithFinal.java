// "Fix all 'Constant expression can be evaluated' problems in file" "true"
class Test {
  private final String field = "field"

  void test() {
    String foo = "Test"<caret> + "test2";
    String foo = "Test" + field;
  }
}