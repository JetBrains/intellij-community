import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  void test(@Nullable String n) {
    switch (<warning descr="Dereference of 'n' may produce 'NullPointerException'">n</warning>) {

    }
  }
}