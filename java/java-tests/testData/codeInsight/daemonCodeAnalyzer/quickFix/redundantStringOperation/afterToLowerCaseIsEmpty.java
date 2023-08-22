// "Remove redundant 'toLowerCase()' call" "true-preview"
class X {
  void test(String string) {
    if (string.isEmpty()) {
      System.out.println("Empty");
    }
  }
}