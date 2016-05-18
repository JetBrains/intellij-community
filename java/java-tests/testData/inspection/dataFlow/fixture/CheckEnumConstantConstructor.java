import org.jetbrains.annotations.*;

enum NotNullEnum {
  ONE("One"),
  TWO(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>),
  THREE(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>) {};

  private final @NotNull String param;
  NotNullEnum(@NotNull String param) {
    this.param = param;
  }
  @NotNull
  public String getParam() {
    return param;
  }
  public static void main(String[] args) {
    NotNullEnum one = ONE;
  }
}