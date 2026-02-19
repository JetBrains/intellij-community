import org.jetbrains.annotations.NotNull;

public class J {
  public String @NotNull [] createArrayFailure(int size) {
    return new String[size];
  }

  public String @NotNull [] createArraySuccess() {
    return new String[] {};
  }
}