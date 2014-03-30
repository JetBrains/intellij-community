import pkg.TA;

class Outer {
  class Middle {
    class Inner {
      void m1(Inner p) { }
      void m2(@TA Outer.Middle.Inner p) { }
      void m3(@TA Middle.Inner p) { }
      void m4(@TA @TA Inner p) { }
    }
  }
}
