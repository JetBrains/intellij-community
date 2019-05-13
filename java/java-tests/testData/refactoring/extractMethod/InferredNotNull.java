import org.jetbrains.annotations.NotNull;

class X {
  public static String main(String[] args) {
    <selection>System.out.println();
    return f();
    </selection>
  }

  @NotNull
  String f() {
    return "";
  }
}
