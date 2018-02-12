// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
class Test {
  public int test(String s1, String s2) {
    if(s1.length() < s2.length()) {
      return -1;
    }
    if((s1.length()) == s2.length()) return 0;
    else /*otherwise bigger*/ return +1;
  }

  public int test2(String s1, String s2) {
    i<caret>f(s1.length() > s2.length()) return -1;
    else if(s2.length() > s1.length()) return 1;
    else return 0;
  }

  public int test3(String s1, String s2) {
    if(s1.length() > s2.length()) return -1;
    else if(s2.length() > s1.length()) return -1;
    else return 0;
  }
}