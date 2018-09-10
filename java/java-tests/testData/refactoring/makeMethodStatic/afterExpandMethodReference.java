import java.util.function.Function;

class Foo {
  Bar frobnitz(Function<Foo, Bar> f) {
    return f.apply(this);
  }
}

class Bar {
  static Bar frob(Bar anObject, Foo foo) {
    return anObject;
  }
}

class Baz {
  public static void main(String[] args) {
    Bar bar = new Bar();
    new Foo().frobnitz(foo -> Bar.frob(bar, foo));
  }
}