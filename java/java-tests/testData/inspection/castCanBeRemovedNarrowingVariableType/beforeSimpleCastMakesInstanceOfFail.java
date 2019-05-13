// "Change type of 'x' to 'String' and remove cast" "false"
class Test {
  void test() {
    Object x = "  hello  ";
    System.out.println(((Str<caret>ing)x).trim());
    if(x instanceof Integer) {
      System.out.println("Impossible");
    }
  }
}