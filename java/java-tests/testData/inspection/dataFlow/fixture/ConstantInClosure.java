import org.jetbrains.annotations.*;

public class ConstantInClosure {
  static final Object CONST = init();

  private void test() {
    Obj obj = new Obj();
    if (CONST != null) {
      privateMethod();
      Runnable runnable = () -> consume(CONST);
    }
  }

  private void privateMethod() { }

  native void consume(@NotNull Object obj);

  @Nullable
  static native Object init();

  private static class Obj { }
}