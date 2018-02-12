// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
class Test {
  public int test(String s1, String s2) {
      return Integer.compare(s1.length(), s2.length());
      /*otherwise bigger*/
  }

  public int test2(String s1, String s2) {
      return Integer.compare(s2.length(), s1.length());
  }

  public int test3(String s1, String s2) {
    if(s1.length() > s2.length()) return -1;
    else if(s2.length() > s1.length()) return -1;
    else return 0;
  }
}