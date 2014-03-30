class Bar {
  Goo getGoo(int a);
  Goo getGoo2(int a);
}
class Goo {}

class Foo {
  Bar getBar() {}
  Bar getBar(int a) {}


  {
    Goo g = <caret>
  }
}