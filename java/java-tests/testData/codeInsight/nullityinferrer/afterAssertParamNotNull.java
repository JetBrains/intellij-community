import org.jetbrains.annotations.NotNull;

class Test {
  public @NotNull String noNull(@NotNull String text) {
    assert text != null;
    return "";
  }

  private void foo() {
    @NotNull String str = "";
    assert str != null;
  }
}