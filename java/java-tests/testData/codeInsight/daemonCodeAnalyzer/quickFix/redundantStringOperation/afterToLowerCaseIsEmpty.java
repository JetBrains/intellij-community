// "Remove redundant 'toLowerCase()' call" "true"
class X {
  void test(String string) {
    if (string.isEmpty()) {
      System.out.println("Empty");
    }
  }
}