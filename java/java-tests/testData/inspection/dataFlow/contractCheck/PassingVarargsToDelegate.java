class Test {
  private String wrap(final String wrapWith, final String token) {
    return join(wrapWith, token, wrapWith);
  }

  @org.jetbrains.annotations.Contract("null->null; !null->!null")
  native static <T> String join(T... elements);
}