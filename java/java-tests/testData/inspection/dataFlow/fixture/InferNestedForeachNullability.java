import foo.*;

class Foo {
  void test2(final @Nullable Object @NotNull[]@NotNull[] values) {
    for (final Object[] line : values) {
      for (final Object item : line) {
        System.out.println(item.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
      }
    }
  }

  void test3(final @Nullable Object @NotNull[]@NotNull[] values) {
    for (Object[] line : values) {
      line = new Object[]{"a"};
      for (Object item : line) {
        System.out.println(item.hashCode());
      }
    }
  }

}