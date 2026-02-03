
final class Test {
  static <T> T create(Iterable<? extends T> iterable) {
    return null;
  }

  public static <T> void testError(Iterable<? extends T> iterable) {
    Runnable r = create(iterable)::toString;
  }

}