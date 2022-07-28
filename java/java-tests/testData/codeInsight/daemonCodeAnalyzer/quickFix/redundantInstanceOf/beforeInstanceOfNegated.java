// "Replace with a null check" "true-preview"
class Test {
  void test(String s) {
    Object obj = s;
    if (!(obj inst<caret>anceof String)) {
      return;
    }
    System.out.println("always");
  }
}