import java.util.function.Supplier;

class RefMain1 {
  {
    Supplier<Foo> s = <caret>
  }
}

class Foo {
  private Foo() {
  }
}
