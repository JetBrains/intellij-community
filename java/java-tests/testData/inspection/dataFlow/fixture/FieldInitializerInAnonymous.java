import org.jetbrains.annotations.Nullable;

class Zoo2 {

  void foo(@Nullable final Object foo, @Nullable final Object bar) {
    if (foo == null) {
      return;
    }

    new Runnable() {
      int hc = foo.hashCode();
      int hc2 = bar.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();

      public void run() {
      }
    }.run();
  }

}
