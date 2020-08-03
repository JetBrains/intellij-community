class X {
  void ok() {
    interface Foo {
      void m();
      default void m2() {}
      static void m3() {}
    }
    Foo foo = () -> {};
  }
  
  void modifiers() {
    <error descr="Modifier 'static' not allowed here">static</error> interface Intf1 {}
    <error descr="Modifier 'private' not allowed here">private</error> interface Intf2 {}
    <error descr="Modifier 'public' not allowed here">public</error> interface Intf3 {}
    <error descr="Modifier 'protected' not allowed here">protected</error> interface Intf3 {}
    abstract interface Intf4 {}
    strictfp interface Intf5 {}
    <error descr="Modifier 'final' not allowed here">final</error> interface Intf6 {}
  }
  
  static int sf = 0;
  
  int f = 2;
  
  void capture() {
    int l = 5;
    final int cst = 2;
    
    interface Foo {
      int x = 2;
      void m();
      default void m2() {
        System.out.println(<error descr="Non-static variable 'l' cannot be referenced from a static context">l</error>);
        System.out.println(<error descr="Non-static field 'f' cannot be referenced from a static context">f</error>);
        System.out.println(sf);
        System.out.println(x);
        System.out.println(<error descr="Non-static variable 'cst' cannot be referenced from a static context">cst</error>);
      }
    }
    
  }
  
  static <T> void typeParameter() {
    interface Foo extends I<String> {
      <error descr="'T' cannot be referenced from a static context">T</error> t = null;
    }
    interface Foo1 extends I<<error descr="'T' cannot be referenced from a static context">T</error>> {}
  }
  
  interface I<Y> {}
  
  class Box<PARAM> {
    void test() {
      interface LocalI {
        <error descr="'X.Box.this' cannot be referenced from a static context">PARAM</error> getParam();
      }
    }
  }
  
}