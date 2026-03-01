import org.jetbrains.annotations.NotNull;

class Test {
  public void foo(final String @NotNull ... arg) {
  }

  {
    foo();
  }
}