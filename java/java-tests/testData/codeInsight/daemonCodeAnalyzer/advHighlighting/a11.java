//statics in inner
public class a {

  static final Number x = null;
  static final int ix = x== null ? 4 : 3;

  class ic {
    <error descr="Inner classes cannot have static declarations">static</error>
      int i;

    <error descr="Inner classes cannot have static declarations">static</error>
      final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    <error descr="Inner classes cannot have static declarations">static</error> 
      final int f2 = x instanceof Integer ? 1 : 0;

    <error descr="Inner classes cannot have static declarations">static</error> 
      class a_ic_c {}

    <error descr="Inner classes cannot have static declarations">interface a_ic_i</error> {}
    <error descr="Inner classes cannot have static declarations">static</error>  interface a_ic_i2 {}

    <error descr="Inner classes cannot have static declarations">static</error> 
      int a_ic_m(String s) { return 0; }

    // static initializer
    <error descr="Inner classes cannot have static declarations">static</error>
    {}
  }


  interface ii {
    static int i = 9;
    void f();
    // since nested interface is implicitly static:
    static class ii_c {}
  }

  // static inside class inside code block
  void f() {
  class ic2 {
    <error descr="Inner classes cannot have static declarations">static</error>
      int i;

    <error descr="Inner classes cannot have static declarations">static</error>
      final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    <error descr="Inner classes cannot have static declarations">static</error> 
      final int f2 = x instanceof Integer ? 1 : 0;

    <error descr="Inner classes cannot have static declarations">static</error> 
      class a_ic_c2 {}

    <error descr="Inner classes cannot have static declarations">static</error> 
      int a_ic_m2(String s) { return 0; }
    // static initializer
    <error descr="Inner classes cannot have static declarations">static</error>
    {}
  }
  }

  void f1() 
  {
    new a() {
    <error descr="Inner classes cannot have static declarations">static</error>
      int i;

    <error descr="Inner classes cannot have static declarations">static</error>
    final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    // its not a compile time constant
    <error descr="Inner classes cannot have static declarations">static</error> 
      final Object o = null;

    <error descr="Inner classes cannot have static declarations">static</error> 
      final int f2 = x instanceof Integer ? 1 : 0;

    <error descr="Inner classes cannot have static declarations">static</error> 
      class a_ic_c2 {}

    <error descr="Inner classes cannot have static declarations">static</error> 
      int a_ic_m2(String s) { return 0; }
    // static initializer
    <error descr="Inner classes cannot have static declarations">static</error>
    {}
    };
  }

  // local interface
  class cc {
    void f() {
      <error descr="Modifier 'interface' not allowed here">interface i</error> {}
    }
    void ff() {
      class inn {
        <error descr="Inner classes cannot have static declarations">interface i</error> {}
      }
    }

    Object o = new Runnable() {
      <error descr="Inner classes cannot have static declarations">interface i</error> {}
      public void run() {}
    };
  }
}

