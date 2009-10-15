class Foo {
  {
    Outer outer;
    outer.new <caret>
  }
}

class Outer{
  class Inner{}
  class AnInner{}
}
