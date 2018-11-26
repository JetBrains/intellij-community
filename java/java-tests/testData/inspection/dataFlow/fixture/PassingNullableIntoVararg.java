import org.jetbrains.annotations.NotNull;

class Test {
  public static void test(@NotNull Object... objects) { }

  public static void test2(@NotNull Object first, @NotNull Object... rest) { }

  public static void main(String[] args) {
    Object o = null;
    if(Math.random() > 0.5) test(o);
    if(Math.random() > 0.5) test2(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">o</warning>);
    if(Math.random() > 0.5) test2(<warning descr="Argument 'o' might be null">o</warning>, o);
  }
}