class X {
  void ok() {
    enum Foo {
      A, B, C;
      void m() {
        
      }
    }
    Foo.A.m();
  }
  
  void modifiers() {
    <error descr="Modifier 'static' not allowed here">static</error> enum E1 {}
    <error descr="Modifier 'private' not allowed here">private</error> enum E2 {}
    <error descr="Modifier 'public' not allowed here">public</error> enum E3 {}
    <error descr="Modifier 'protected' not allowed here">protected</error> enum E4 {}
    <error descr="Modifier 'abstract' not allowed here">abstract</error> enum E5 {}
    strictfp enum E6 {}
    <error descr="Modifier 'final' not allowed here">final</error> enum E7 {}
  }
  
  static int sf = 0;
  
  int f = 2;
  
  void capture() {
    int l = 5;
    final int cst = 2;
    
    enum Foo {
      A, B, C {
        void m() {
          System.out.println(x);
          System.out.println(<error descr="Non-static variable 'l' cannot be referenced from a static context">l</error>);
          System.out.println(<error descr="Non-static field 'f' cannot be referenced from a static context">f</error>);
          System.out.println(sf);
          System.out.println(<error descr="Non-static variable 'cst' cannot be referenced from a static context">cst</error>);
        }
      };
      
      int x = 2;
      void m() {}
    }
    
  }
  
  <U> U getAndSet(U u) {
    enum Xyz {
      A;
      <error descr="'U' cannot be referenced from a static context">U</error> u;
    }
    U old = Xyz.A.u;
    Xyz.A.u = u;
    return old;
  }
  
}