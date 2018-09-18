// "Sort content" "false"

enum E {
  Foo(null),
  Bar(Foo),<caret>
  Baz(null);

  E(E e) {

  }
}
