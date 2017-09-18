import org.jetbrains.annotations.NotNull;

class Test {
  @NotNull
  String foo() {
    return "";
  }

  void m() {
    foo().opt<caret>
  }
}