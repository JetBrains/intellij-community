import org.jetbrains.annotations.NotNull;

class Test {
  static void someMethod(@NotNull Object... varargParameter) {

  }

  public static void main(String[] args) {
    someMethod(null, null);
  }
}
