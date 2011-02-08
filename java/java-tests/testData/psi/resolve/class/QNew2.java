class A {
  {
    Outer outer;
    outer.new <ref>Inner(){};
  }
}

class Outer{
  class Inner{}
}