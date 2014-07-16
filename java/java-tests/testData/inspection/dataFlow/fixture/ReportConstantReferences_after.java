import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  private void test2(@NotNull Object bar) {}

  void println(@Nullable Object o) {}

  private Object test(Object foo, Object bar) {
    if (foo == null) {
      println(<caret>null);
      println(foo);
      return foo;
    }
    if (bar == null) {
      test2(bar);
    }
    return foo;
  }

  public void testDontReplaceQualifierWithNull(Object bar) {
    if (bar == null) {
      bar.hashCode();
    }
  }

}