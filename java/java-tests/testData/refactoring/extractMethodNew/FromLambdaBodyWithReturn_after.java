import org.jetbrains.annotations.NotNull;

class Test {
  interface I {
    String foo();
  }
  public void foo(int ii) {
    I r = () -> {
        return newMethod();
    };
  }

    private @NotNull String newMethod() {
        return "42";
    }
}
