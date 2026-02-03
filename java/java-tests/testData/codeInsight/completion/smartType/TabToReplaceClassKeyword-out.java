class Foo {
  void foo() {
    Class<? extends Foo> c = Foo.class;<caret>
  }
}

class Bar extends Foo {}