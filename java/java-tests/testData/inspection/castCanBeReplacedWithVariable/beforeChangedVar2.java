// "Replace '(String) obj' with 's1'" "false"

class X {
  void test(Object obj) {
    String s1 = (String) obj, s2 = s1 = "blah blah blah";
    String s3 = (String) ob<caret>j;
  }
}