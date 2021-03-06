//statics in inner -- in Java 16 allowed everywhere
public class a {

  static final Number x = null;
  static final int ix = x== null ? 4 : 3;

  class ic {
    static int i;

    static final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    static final int f2 = x instanceof Integer ? 1 : 0;

    static class a_ic_c {}

    interface a_ic_i {}
    static  interface a_ic_i2 {}

    static int a_ic_m(String s) { return 0; }

    static {}
  }


  interface ii {
    static int i = 9;
    void f();
    static class ii_c {}
  }

  // static inside class inside code block
  void f() {
  class ic2 {
    static int i;

    static final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    static final int f2 = x instanceof Integer ? 1 : 0;

    static class a_ic_c2 {}

    static int a_ic_m2(String s) { return 0; }
    // static initializer
    static {}
  }
  }

  void f1()
  {
    new a() {
    static int i;

    static final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

    static final Object o = null;

    static final int f2 = x instanceof Integer ? 1 : 0;

    static class a_ic_c2 {}

    static int a_ic_m2(String s) { return 0; }
    // static initializer
    static {}
    };
  }

  // local interface
  class cc {
    void f() {
      interface i {}
    }
    void ff() {
      class inn {
        interface i {}
      }
    }

    Object o = new Runnable() {
      interface i {}
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

