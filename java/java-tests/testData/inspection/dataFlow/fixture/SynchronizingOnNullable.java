import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  void test(@Nullable Object n) {
    synchronized (<warning descr="Dereference of 'n' may produce 'NullPointerException'">n</warning>) {

    }
  }
}