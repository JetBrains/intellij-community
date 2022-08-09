// "Replace with a null check" "true-preview"
class Test {
  void test(String s) {
    if(s != null) {
      System.out.println("always");
    }
  }
}