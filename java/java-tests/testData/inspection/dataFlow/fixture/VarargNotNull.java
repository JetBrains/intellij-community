import foo.*;

abstract class Foo {
  void foo(Object @NotNull ... arrayItems) {}

  @Nullable Object get() { return null;}

  {
    foo(get());
    foo(get(), get());
  }
}

