import org.jetbrains.annotations.NotNull;

class X {
  private final String s;

  public X(String s) {
    this.s = newMethod(s);
  }

    private @NotNull String newMethod(String s) {
        return s.toString();
    }
}
