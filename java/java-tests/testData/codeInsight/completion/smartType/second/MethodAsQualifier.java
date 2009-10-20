class Bar {
  Goo getGoo();
}
class Goo {}

class Foo {
  Bar getBar() {}


  {
    Goo g = <caret>
  }
}