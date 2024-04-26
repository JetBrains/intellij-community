public class Test {
  public static void main(String[] args) {
    testString("World ");
    testString(null);
  }

  static void testString(String s) {
    if (s == null) {
      System.out.println("oops");
    }
    if (s != null) {
      s += "hello";
      System.out.println(s);
    }
    s += null;
    System.out.println(s.trim());
    s = null;
  }
}