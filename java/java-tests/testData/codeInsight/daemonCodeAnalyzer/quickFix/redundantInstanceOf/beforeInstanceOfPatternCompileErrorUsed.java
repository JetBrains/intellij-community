// "Replace with a null check" "false"
class Test {
  void test(String s) {
    if(s instanceof <caret>String s1) {
      System.out.println("always: " + s1);
    }
  }
}