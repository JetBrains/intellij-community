class Test {

  public void testConsume() {
    consume(exception());
  }

  public static void consume(Throwable t) {}

  public static void consume(String s) {}

  public static <E extends Exception> E exception() {
    return null;
  }
}