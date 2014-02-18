import org.jetbrains.annotations.NotNull;

class ASD {
  static void foo(Object any) {
    boolean a = false;
    boolean b = false;
    while (true) {
      boolean trg = bar() == any;

      a = a || trg;
      b = b || !trg;

      if (b && a) break;
    }
  }

  @NotNull
  static Object bar() {
    return new Object();
  }
}
