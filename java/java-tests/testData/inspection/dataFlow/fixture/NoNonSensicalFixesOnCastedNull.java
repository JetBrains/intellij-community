import org.jetbrains.annotations.NotNull;

class Foo {
  void main() {
    foo(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">(String) null</warning>);
  }

  static void foo(@NotNull String s) {}
}