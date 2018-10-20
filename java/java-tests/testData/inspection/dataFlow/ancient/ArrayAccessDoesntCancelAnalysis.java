import org.jetbrains.annotations.*;

class Test {
  private static void foo(@NotNull String smth) {
  }

  public static void main(String[] args) {
    String s = args[0];
    foo(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
}