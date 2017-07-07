import org.jetbrains.annotations.NotNull;

class Foo {
  void main() {
    foo(<warning descr="Argument '(String) null' might be null">(String) nu<caret>ll</warning>);
  }

  static void foo(@NotNull String s) {}
}