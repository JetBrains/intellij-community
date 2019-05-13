// "Change type of 'x' to 'String' and remove cast" "true"
class Test {
  void test() {
    Object x = "  hello  ";
    System.out.println(((Str<caret>ing)x).trim());
    System.out.println(((String)x).substring(1));
  }
}