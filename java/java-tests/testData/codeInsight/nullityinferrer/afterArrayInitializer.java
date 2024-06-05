import org.jetbrains.annotations.NotNull;

public class J {
  @NotNull
  public String[] createArrayFailure(int size) {
    return new String[size];
  }

  @NotNull
  public String[] createArraySuccess() {
    return new String[] {};
  }
}