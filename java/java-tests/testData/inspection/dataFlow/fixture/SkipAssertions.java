import org.jetbrains.annotations.NotNull;

class Test {
  private static void test(@NotNull Object foo) {
    assert foo != null;
  }

  private static void testParens(@NotNull Object foo) {
    assert (foo != null);
  }

  private static void test2(@NotNull Object foo) {
    if (foo == null) {
      throw new IllegalArgumentException();
    }
  }
  private static void test3(@NotNull Object foo) {
    if (foo == null) throw new IllegalArgumentException();
  }

  private static void test4(@NotNull Object foo) {
    if (<warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>) throw new IllegalArgumentException();
  }

}