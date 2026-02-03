public class a {
  void f1() 
  {
    new a() {
    <error descr="Inner classes cannot have static declarations">static</error>
      int i;

    // compile time constant is OK
    static final int f1 = 3 < 4 ? (a.ix==5 ?  1 : 3) / 4 + 18 : 0;

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
}

