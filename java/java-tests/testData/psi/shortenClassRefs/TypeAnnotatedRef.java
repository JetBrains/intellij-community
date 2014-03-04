import pkg.TA;

class Outer {
  class Middle {
    class Inner {
      void m1(Outer.Middle.Inner p) { }
      void m2(@pkg.TA Outer.Middle.Inner p) { }
      void m3(Outer.@pkg.TA Middle.Inner p) { }
      void m4(Outer.Middle.@pkg.TA @pkg.TA Inner p) { }
    }
  }
}
