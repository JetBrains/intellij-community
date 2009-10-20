class Foo {
  {
    Outer outer;
    outer.new I<caret>
  }
}

class Outer{
  class Inner{}
  class IInner{}
}
