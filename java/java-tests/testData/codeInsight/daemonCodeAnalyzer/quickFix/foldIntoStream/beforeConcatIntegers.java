// "Fold expression into Stream chain" "true"
class Test {
  void test2(Integer a, Integer b, Integer c, Integer d) {
    String result = a + "," + b + "," + c + "," <caret>+ d + ",";
  }
}