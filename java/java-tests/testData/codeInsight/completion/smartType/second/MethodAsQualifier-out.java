class Bar {
  Goo getGoo();
}
class Goo {}

class Foo {
  Bar getBar() {}


  {
    Goo g = getBar().getGoo();<caret>
  }
}