// "Change type of 'x' to 'String' and remove cast" "false"
class Test {
  void test() {
    Object x = "  hello  ";
    System.out.println(((Str<caret>ing)x).trim());
    foo(x);
  }

  void foo(Object obj) {
    System.out.println("Obj");
  }

  void foo(String s) {
    System.out.println("String");
  }
}