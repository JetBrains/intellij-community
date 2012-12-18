import org.jetbrains.annotations.Nullable;

class Zoo2 {

  void foo(@Nullable Object foo) {
    if (foo == null) {
      return;
    }

    new Runnable() {
      int hc = foo.hashCode();

      public void run() {
      }
    }.run();
  }

}
