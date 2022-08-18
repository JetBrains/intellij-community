// "Replace with a null check" "true-preview"
class Test {
  void test(String s) {
    Object obj = s;
    if (obj == null) {
      return;
    }
    System.out.println("always");
  }
}