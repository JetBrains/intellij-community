// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
public class Test {
  public void test(String s1, String s2) {
    System.out.println(Integer.compare(s1.length(), s2.length()));
    System.out.println(Integer.compare(s2.length(), s1.length()));
    /*greater!*/
    /*less!*/
    /*equal!*/
      System.out.println(Integer.compare(s1.length(), s2.length()));
    System.out.println(Integer.compare(s2.length(), s1.length()));
    System.out.println(Integer.compare(s2.length(), s1.length()));

    System.out.println(s1.length() < s2.length() ? -1 : s1.length() == s2.length() ? 0 : 2);
    System.out.println(s1.length() < s2.length() ? 1 : s2.length() < s1.length() ? 0 : 1);
    System.out.println(s1.length() == s2.length() ? 1 : s2.length() < s1.length() ? 0 : 1);
    System.out.println(s1.length() == s2.length() ? 0 : s2.length() < s2.length() ? -1 : 1);
  }
}