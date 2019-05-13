
class Test {
  {
    assertEquals(100, (int)get());
  }

  private <T> T get() {
    return null;
  }


  public static void assertEquals(long expected, long actual) {}
  public static void assertEquals(double expected, double actual) {}
  public static void assertEquals(Object expected, Object actual) {}

}
