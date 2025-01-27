// reference before ctr called
import java.io.*;
import java.lang.Override;
import java.lang.String;

class A {
 A(int i) {}
 A(A a) {}
 int f() { return 0; }
 int fi;
} 	

class B extends A {
  int bi;
  B(int h) {
    super(<error descr="Cannot reference 'B.bi' before superclass constructor is called">bi</error>);
  }
  B() {
    this(<error descr="Cannot reference 'B.bi' before superclass constructor is called">bi</error>);
  }

  B(String s) {
    super(<error descr="Cannot call 'B.db()' before superclass constructor is called">db</error>(1) );
  }

  B(int i, int j) {
    super(<error descr="Cannot call 'A.f()' before superclass constructor is called">f</error>());
  }
  B(int i, int j, int k) {
    super(<error descr="Cannot reference 'super' before superclass constructor is called">super</error>.f());
  }

  B(String s, int i) {
    super(s.length());
  }

  B(int s, int i, char j) {
    super(<error descr="Cannot reference 'super' before superclass constructor is called">super</error>.fi );
  }

  B(double d) {
    super(new <error descr="Cannot reference 'Inner' before superclass constructor is called">Inner</error>() );
  }
  class Inner extends A {
    Inner(){
      super(1);
    }
  }

  int db(int j) { 
   return 0;
  }
}


class Enc {
  int ienc;
  class Bb extends A {
    int ibb;
    Bb() { super(ienc); }
    Bb(int i) {
      super(i);
    }

    Bb(int i, int j) {
      super(<error descr="Cannot reference 'Bb.this' before superclass constructor is called">Enc.Bb.this</error>.ibb );
    }

    Bb(int i, String s) {
      super(Enc.this.ienc);
    }

    Bb(int i, char j) {
      super(<error descr="Cannot reference 'this' before superclass constructor is called">this</error> );
    }
  }

  Enc() {
    this(new <error descr="Cannot reference 'Bb' before superclass constructor is called">Bb</error>());
  }
  Enc(Bb b) {}
}

// static are OK
class C2 extends A {
  static final int fi = 4;
  C2() {
    super(fi);
  }
  C2(int i) {
    super(sf());
  }
  static int sf() { return 0; }

  C2(int i, int j) {
    super(new Sc().i);
  }
  static class Sc {
    int i;
  }
}

interface Callback {
    void call();
}

class Base {
    Callback callback;

    public Base(final Callback callback) {
        this.callback = callback;
    }
}

class YellinBug extends Base {
    public YellinBug() {
        super(new Callback() {

            public void call() {
               <error descr="Cannot reference 'YellinBug.this' before superclass constructor is called">YellinBug.this</error>.f();
            }
        });
    }

    private void f() {}

    {
        new Callback() {

            public void call() {
                YellinBug.this.f();
            }
        };
    }
}

class Outer {
  class Inner extends Outer{}
  class UseIt extends Inner{
    Outer o;
    UseIt() {
      <error descr="Cannot reference 'UseIt.o' before superclass constructor is called">o</error>.super();
    }

    Outer geto() {
     return null;
    }
    UseIt(int x) {
      <error descr="Cannot call 'UseIt.geto()' before superclass constructor is called">geto</error>().super();
    }
    UseIt(Outer x) {
      <error descr="Cannot reference 'this' before superclass constructor is called">this</error>.super();
    }
  }
}

class WithAnonymous {
  static class SuperClass {
      public void foo() {}
  } 
  class ChildClass extends SuperClass {
  
      public ChildClass(final String title) {
          this(new SuperClass(){
              {
                  foo();
              }
          });
      }
  
      public ChildClass(SuperClass child) {
      }
  
  }
}

class InnerClassRefInsideAnonymous {
    static class Foo {}
    static class SuperClass {
      SuperClass(Foo foo) {
      }
      
      SuperClass(String s, Foo foo) {
      }
    }
    
    static class Child extends SuperClass {
      Child(Foo foo) {
        super(new Foo() {
          public String toString() {
            AFoo afoo = new <error descr="Cannot reference 'AFoo' before superclass constructor is called">AFoo</error>();
            return super.toString();
          }
        });
      }

      Child(String s, Foo foo) {
        super(s, new <error descr="Cannot reference 'AFoo' before superclass constructor is called">AFoo</error>());
      }

      class AFoo extends Foo {} 
    }
}

interface MarkerInterface { }

class Enclosing {
  class Inner { }

  Enclosing() {
    Inner inner = new Inner();

    class MyLocal implements MarkerInterface {
      MyLocal() {
        super();
      }
    }
  }
}