import org.jetbrains.annotations.NotNull;

class Test {
  @NotNull
  public String noNull(@NotNull String text) {
    assert text != null;
    return "";
  }

  private void foo() {
    @NotNull String str = "";
    assert str != null;
  }
}