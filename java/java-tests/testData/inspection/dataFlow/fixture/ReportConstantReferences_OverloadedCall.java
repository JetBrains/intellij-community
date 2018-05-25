import org.jetbrains.annotations.Nullable;

class Test {
  void method(@Nullable String o) {}

  void method(@Nullable Integer o) {}

  private void test(String foo) {
    if (foo == null) {
      method(<weak_warning descr="Value 'foo' is always 'null'"><caret>foo</weak_warning>);
    }
  }

}