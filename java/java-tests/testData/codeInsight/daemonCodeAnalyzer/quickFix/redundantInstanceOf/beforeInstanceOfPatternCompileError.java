// "Replace with a null check" "true"
class Test {
  void test(String s) {
    if(s instanceof <caret>String s1) {
      System.out.println("always");
    }
  }
}