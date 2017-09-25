import org.jetbrains.annotations.NotNull;

class Test {
  @NotNull
  String foo() {
    return "";
  }

  void m() {
      java.util.Optional.of(foo())
  }
}