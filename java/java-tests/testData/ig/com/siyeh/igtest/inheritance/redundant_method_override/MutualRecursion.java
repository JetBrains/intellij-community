///////////////
class X7 {
  Object x() {
    return new Object() {
      void a() {
        b(); // used before declaration
      }
      void b() {
        a();
      }
    };
  }
}
class X8 extends X7 {
  @java.lang.Override
  Object <warning descr="Method 'x()' is identical to its super method">x</warning>() {
    return new Object() {
      void b(){
        a();
      }
      void a() {
        b();
      }
    };
  }
}
