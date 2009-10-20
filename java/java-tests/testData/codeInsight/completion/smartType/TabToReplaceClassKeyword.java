class Foo {
  void foo() {
    Class<? extends Foo> c = F<caret>a.class;
  }
}

class Bar extends Foo {}