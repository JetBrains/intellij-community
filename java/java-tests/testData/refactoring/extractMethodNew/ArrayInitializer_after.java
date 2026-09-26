import org.jetbrains.annotations.NotNull;

class Test {
  void foo() {
    int[] arr = newMethod();
  }

    private int @NotNull [] newMethod() {
        return new int[]{1, 2, 3};
    }
}