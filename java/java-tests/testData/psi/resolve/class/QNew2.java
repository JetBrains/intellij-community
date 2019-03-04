class A {
  {
    Outer outer;
    outer.new <caret>Inner(){};
  }
}

class Outer{
  class Inner{}
}