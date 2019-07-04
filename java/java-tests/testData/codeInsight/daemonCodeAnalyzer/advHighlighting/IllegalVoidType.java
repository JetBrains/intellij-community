class C {
  <error descr="Illegal type: 'void'">void</error>[] <error descr="Invalid return type">m1</error>() { }
  <error descr="Illegal type: 'void'">void</error> <error descr="Invalid return type">m2</error>()[] { }
  void m3(<error descr="Illegal type: 'void'">void</error> p) {}

  {
    <error descr="Illegal type: 'void'">void</error>[] va;
    <error descr="Illegal type: 'void'">void</error> vv;
    Object oo = new <error descr="Illegal type: 'void'">void</error>[1];

    // this is the only valid void usage
    Class voidClass = void.class;
  }

  void f() {
    <error descr="Incompatible types. Found: 'void', required: 'java.lang.Object'">Object o = f();</error>
  }
}
