import java.lang.Object;

class IFoo {
  Object getValue() {}
}

class Foo extends IFoo {
  Foo getValue() {}
  void foo(IFoo o) {
    if (o instanceof Foo) {
      o.getv<caret>x
    }
  }
}