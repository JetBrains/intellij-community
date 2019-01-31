import org.jetbrains.annotations.NotNull;

class NotNullAnonymousConstructor {
  void test() {
    new Foo(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>) {};
  }
  
  class Foo {
    Foo(@NotNull Object x) {}
  }
}