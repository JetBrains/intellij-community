// "Replace with a null check" "false"
class Test {
  void test(String s) {
    Object object = s;
    if(object instanceof <caret>String s1) {
      System.out.println("always: " + s1);
    }
  }
}