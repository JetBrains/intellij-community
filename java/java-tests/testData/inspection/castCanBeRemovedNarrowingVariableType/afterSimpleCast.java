// "Change type of 'x' to 'String' and remove cast" "true"
class Test {
  void test() {
    String x = "  hello  ";
    System.out.println(x.trim());
    System.out.println(x.substring(1));
  }
}