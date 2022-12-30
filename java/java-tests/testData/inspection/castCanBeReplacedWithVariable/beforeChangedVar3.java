// "Replace '(String) obj' with 's1'" "false"

class X {
  void test(Object obj, String s) {
    if (obj instanceof String s1 && (s1 = "blah blah blah").equals(s)) {
      String s2 = (String) ob<caret>j;
    }
  }
}