class Test {
  public static void testFunc(final float width, final float height) {
    if (width < 0f || height < 0f) {
      throw new IllegalArgumentException("Size must be non-negative");
    }
  }
}