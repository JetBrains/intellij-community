class Bar {
  Goo goo;
}
class Goo {}

class Foo {
  Bar b;


  {
    Goo g = b.goo;<caret>
  }
}