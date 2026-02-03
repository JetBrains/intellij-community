import org.jetbrains.annotations.Nullable;

class Foo {

  void foo(final @Nullable String s, String s2) {
    if (s2 == null) return;
    if (s == null) return;

    System.out.println(s2);

    new Runnable() {
      @Override
      public void run() {
        s.hashCode();
      }
    }.run();
  }

}