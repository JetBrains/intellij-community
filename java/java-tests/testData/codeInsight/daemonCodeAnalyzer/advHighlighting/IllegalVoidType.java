class C {
  <error descr="Illegal type: 'void'">void</error>[] m1() { }
  <error descr="Illegal type: 'void'">void</error> m2()[] { }
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
