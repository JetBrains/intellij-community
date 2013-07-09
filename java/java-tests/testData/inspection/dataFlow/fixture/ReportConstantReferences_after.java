import org.jetbrains.annotations.NotNull;

class Test {
  private void test2(@NotNull Object bar) {

  }
  private Object test(Object foo, Object bar) {
    if (foo == null) {
      System.out.println(<caret>null);
      System.out.println(foo);
      return foo;
    }
    if (bar == null) {
      test2(bar);
    }
    return foo;
  }

}