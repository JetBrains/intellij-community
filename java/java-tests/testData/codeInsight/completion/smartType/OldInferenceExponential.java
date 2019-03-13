class Foo {
  public void test() throws ParseException {
    assertThat(eq("---"),
    assertThat(eq("---"),
    assertThat(eq("fooo"),
    assertThat(eq("---"),
    assertThat(eq("---"),
    assertThat(eq("---"),
    assertThat(eq("---"),
    assertThat(eq(new byte[0]),
    assertThat(eq((Integer) null),
    assertThat(eq((Integer) null),
    assertThat(eq("---"),
    assertThat(eq((String) null),
    assertThat(eq("---"),
    assertThat(eq("---"),
    assertThat(<caret>eq("IO")
  }

  public static native <T> T eq(T value);

  public static <T> void assertThat(T actual, Matcher<? super T> matcher) {}
  public static <T> void assertThat(String reason, T actual, Matcher<? super T> matcher) {}
  
  interface Matcher<T> {}
}