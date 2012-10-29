import org.jetbrains.annotations.Nullable;

public class BrokenAlignment {

  void test(@Nullable Object n) {
    synchronized (<warning descr="Dereference of 'n' may produce 'java.lang.NullPointerException'">n</warning>) {

    }
  }
}