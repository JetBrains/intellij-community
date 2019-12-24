// "Remove redundant 'toLowerCase()' call" "true"
class X {
  void test(String string) {
    if (string.to<caret>LowerCase().isEmpty()) {
      System.out.println("Empty");
    }
  }
}