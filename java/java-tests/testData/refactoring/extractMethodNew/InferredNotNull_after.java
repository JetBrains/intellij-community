import org.jetbrains.annotations.NotNull;

class X {
  public static String main(String[] args) {
      return newMethod();

  }

    private static @NotNull String newMethod() {
        System.out.println();
        return f();
    }

    @NotNull
  String f() {
    return "";
  }
}
