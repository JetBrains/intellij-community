//statics in inner
public class a {

  static final Number x = null;
  static final int ix = x== null ? 4 : 3;

  class ic {
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      int i;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      final int f2 = x instanceof Integer ? 1 : 0;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      class a_ic_c {}

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">interface a_ic_i</error> {}
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>  interface a_ic_i2 {}

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      int a_ic_m(String s) { return 0; }

    // static initializer
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
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
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      int i;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      final int f2 = x instanceof Integer ? 1 : 0;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      class a_ic_c2 {}

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      int a_ic_m2(String s) { return 0; }
    // static initializer
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
    {}
  }
  }

  void f1()
  {
    new a() {
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      int i;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
    final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    // its not a compile time constant
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      final Object o = null;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      final int f2 = x instanceof Integer ? 1 : 0;

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      class a_ic_c2 {}

    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
      int a_ic_m2(String s) { return 0; }
    // static initializer
    <error descr="Static declarations in inner classes are not supported at language level '1.4'">static</error>
    {}
    };
  }

  // local interface
  class cc {
    void f() {
      <error descr="Local interfaces are not supported at language level '1.4'">interface</error> i {}
    }
    void ff() {
      class inn {
        <error descr="Static declarations in inner classes are not supported at language level '1.4'">interface i</error> {}
      }
    }

    Object o = new Runnable() {
      <error descr="Static declarations in inner classes are not supported at language level '1.4'">interface i</error> {}
      public void run() {}
    };
  }

  void withanonymous() {
    new Object() {
      <error descr="Modifier 'private' not allowed here">private</error> class RT {}
      private void method() {}
      private int myI;
    };
  }
}

