import org.jetbrains.annotations.Nullable;

class Zoo2 {

  void foo(@Nullable Object foo, @Nullable Object bar) {
    if (foo == null) {
      return;
    }

    new Runnable() {
      int hc = foo.hashCode();
      int hc2 = <warning descr="Method invocation 'bar.hashCode()' may produce 'java.lang.NullPointerException'">bar.hashCode()</warning>;

      public void run() {
      }
    }.run();
  }

}
