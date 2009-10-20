class Bar {
  Goo getGoo(int a);
  Goo getGoo();
}
class Goo {}

class Foo {
  Bar getBar() {}


  {
    Goo g = <caret>
  }
}