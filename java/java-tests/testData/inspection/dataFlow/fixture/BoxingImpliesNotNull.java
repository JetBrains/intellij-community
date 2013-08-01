import org.jetbrains.annotations.Nullable;

class Baz {
  Integer bar() { return 2; }

  void g() {
    @Nullable Integer foo = bar();
    if (foo == null) {
      foo = 0;
    }
    System.out.println(foo.intValue());
  }

}