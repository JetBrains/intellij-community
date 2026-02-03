class MainTest {
  static void test(String <flown1>str) {
    System.out.println(<caret>str.trim());
  }

  public static void main(String[] args) {
    foo("xyz");
    foo(<flown1111>null);
    bar("xyz");
    bar(null);
  }

  private static void bar(String s) {
    test(s);
  }

  private static void foo(String <flown111>s) {
    test(<flown11>s);
  }
}