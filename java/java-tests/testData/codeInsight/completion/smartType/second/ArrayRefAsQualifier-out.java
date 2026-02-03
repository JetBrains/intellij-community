class Bar {
  Goo getGoo();
}
class Goo {}

class Foo {
  {
    Bar[] ref = new Bar[1];
    Goo g = ref[0].getGoo();<caret>
  }
}