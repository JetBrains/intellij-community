// "Replace 's' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (!(obj instanceof String)) {
      System.out.println("not");
    } else {
      String <caret>s = (String)obj;
    }
  }
}