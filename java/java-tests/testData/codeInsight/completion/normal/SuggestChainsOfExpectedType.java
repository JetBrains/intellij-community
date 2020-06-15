class Bar {
  Goo getGoo();
}
class Goo {}

class Foo {
  Bar bar() {}

  void x(Goo unmatched) {
    Goo g = ba<caret>
  }
}