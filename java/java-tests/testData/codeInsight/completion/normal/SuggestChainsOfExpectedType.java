class Bar {
  Goo getGoo();
}
class Goo {}

class Foo {
  Bar getBar() {}


  void x(Goo unmatched) {
    Goo g = getG<caret>
  }
}