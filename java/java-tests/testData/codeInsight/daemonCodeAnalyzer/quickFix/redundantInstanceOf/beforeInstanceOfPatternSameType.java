// "Replace with a null check" "true-preview"
class Test {
  void test(String s) {
    if(s instanceof <caret>String s1) {
      System.out.println("always");
    }
  }
}