import org.jetbrains.annotations.NotNull;

class Foo {
  void main() {
    foo((String) <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }

  static void foo(@NotNull String s) {}
}