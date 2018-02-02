
class Test {
  static class IterableSubject {}

  public static <J> IterableSubject bar(Iterable<J> target) {
    return foo(target);
  }

  public static <J> IterableSubject foo(Iterable<J> target) {
    return null;
  }

}

abstract class Test1 {
  private class XBreakpoint<P> {}
  private class XBreakpointType<T extends XBreakpoint<P>, P> {}

  abstract <B extends XBreakpoint<?>> XBreakpointType<B, ?>
  findBreakpointType( Class<? extends XBreakpointType<B, ?>> typeClass);

  <U extends XBreakpoint<?>> void createXBreakpoint(Class<? extends XBreakpointType<U, ?>> typeCls) {
    final XBreakpointType<U, ?> type = findBreakpointType(typeCls);
  }

}

class Test2 {
  static abstract class AbstractIterableAssert<S extends AbstractIterableAssert<S, A, T>, A extends Iterable<? extends T>, T> {}

  public static <T1> AbstractIterableAssert<?, ? extends Iterable<? extends T1>, T1> bar(Iterable<? extends T1> actual) {
    return foo(actual);
  }

  public static <T> AbstractIterableAssert<?, ? extends Iterable<? extends T>, T> foo(Iterable<? extends T> actual) {
    return null;
  }
}