class MyTest {
  private static Object foo(String s, boolean b) {
    return b ? (Object) Double.parseDouble(s) : (Object) Long.parseLong(s);
  }
}