class Bar {
  Goo getGoo(int a);
}
class Goo {}

class Foo {
  Bar getBar() {}


  {
    Goo g = getBar().getGoo(<caret>);
  }
}