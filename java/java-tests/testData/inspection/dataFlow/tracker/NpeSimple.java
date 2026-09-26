/*
May be null (s; line#12)
  An execution might exist where:
    's' is known to be 'null' from line #9 (s == null; line#9)
 */

class Test {
  void test(String s) {
    if (s == null) {
      System.out.println(s);
    }
    System.out.println(<selection>s</selection>.trim());
  }
}