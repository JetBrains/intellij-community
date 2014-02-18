import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  void test(@Nullable Object n) {
    synchronized (<warning descr="Dereference of 'n' may produce 'java.lang.NullPointerException'">n</warning>) {

    }
  }
}