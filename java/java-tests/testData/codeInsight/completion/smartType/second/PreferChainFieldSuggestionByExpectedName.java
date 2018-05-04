class Bar {
  Goo a;
  Goo b;
  Goo superclass;
}
class Goo {}

class Foo {
  Bar b;

  void method(Goo superClass) {} //note case difference

  {
    method(<caret>)
  }
}