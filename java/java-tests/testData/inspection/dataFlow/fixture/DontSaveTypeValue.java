import org.jetbrains.annotations.*;

class TestIDEAWarn {
  public int bar() {
    if (Foo.BAR == foo(0)) {
      return -1;
    }
    else if (Foo.BAR == foo(1)) {
      return 1;
    }

    return 0;
  }

  @NotNull
  private native Foo foo(int a);

  enum Foo {
    FOO, BAR
  }

}