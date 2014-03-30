import org.jetbrains.annotations.NotNull;

class Test {
  private void test2(@NotNull Object bar) {

  }
  private Object test(Object foo, Object bar) {
    if (foo == null) {
      System.out.println(<warning descr="Value 'foo' is always 'null'"><caret>foo</warning>);
      System.out.println(<warning descr="Value 'foo' is always 'null'">foo</warning>);
      return <warning descr="Expression 'foo' might evaluate to null but is returned by the method which is not declared as @Nullable">foo</warning>;
    }
    if (bar == null) {
      test2(<warning descr="Argument 'bar' might be null">bar</warning>);
    }
    return foo;
  }

}