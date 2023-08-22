import typeUse.*;

// IDEA-245323
class Test {
  void test0(final Object @NotNull [] @Nullable [] values) {
    for (final Object[] line : values) {
      for (final Object item : <warning descr="Dereference of 'line' may produce 'NullPointerException'">line</warning>) {
      }
    }
  }
  void test1(final Object @Nullable[] @NotNull[] values) {
    for (final Object[] line : <warning descr="Dereference of 'values' may produce 'NullPointerException'">values</warning>) {
      for (final Object item : line) {
      }
    }
  }
}