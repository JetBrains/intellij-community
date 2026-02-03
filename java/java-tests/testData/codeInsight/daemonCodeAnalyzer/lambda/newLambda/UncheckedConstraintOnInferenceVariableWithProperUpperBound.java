import java.util.function.Consumer;


interface A<T> {
  void locateDefinition();
}

class Test {

  public static <T extends A> void bar(final T member, final Consumer<T> processor) {}
  public static <T extends A<?>> void bar1(final T member, final Consumer<T> processor) {}
  public static <T extends A<T>> void bar2(final T member, final Consumer<T> processor) {}

  public static void foo(final A member) {
    bar(member, symbol -> symbol.locateDefinition());
    bar1(member, symbol -> symbol.locateDefinition());
    bar2(member, symbol -> symbol.locateDefinition());
  }
}
