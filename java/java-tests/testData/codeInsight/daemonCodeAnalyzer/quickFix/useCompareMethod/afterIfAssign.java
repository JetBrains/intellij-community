// "Fix all ''compare()' method can be used to compare numbers' problems in file" "true"
class Test {
  public void test(String s1, String s2) {
    int res = Integer.compare(s2.length(), s1.length());
      System.out.println(res);
  }

  public void testMissingElse(String s1, String s2) {
    int res;
    if(s1.length() < s2.length()) res = 1;
    else if(s1.length() > s2.length()) res = -1;
    res = 0;
    System.out.println(res);
  }
}