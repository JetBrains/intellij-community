class s {
  void f() {
    // illegal type
    <error descr="Illegal type: 'void'">void</error>[] va;
    <error descr="Illegal type: 'void'">void</error> vv;
    class loc {
      void f(<error descr="Illegal type: 'void'">void</error> i) {}
    }
    Object o = new <error descr="Illegal type: 'void'">void</error>[1];

    // this is the only valid void usage
    Class voidClass = void.class;
  }

  {
    <error descr="Incompatible types. Found: 'void', required: 'java.lang.Object'">Object o = f();</error>
  }
}
