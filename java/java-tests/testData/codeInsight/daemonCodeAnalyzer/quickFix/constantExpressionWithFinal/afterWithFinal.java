// "Fix all 'Constant expression can be evaluated' problems in file" "true"
class Test {
  private final String field = "field"

  void test() {
    String foo = "Testtest2";
    String foo = "Test" + field;
  }
}