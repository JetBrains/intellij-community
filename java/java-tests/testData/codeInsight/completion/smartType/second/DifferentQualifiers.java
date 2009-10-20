class Bar {
  Goo getGoo(int a);
}
class Goo {}

class Foo {
  Bar getBar() {}
  Bar b;


  {
    Goo g = <caret>
  }
}