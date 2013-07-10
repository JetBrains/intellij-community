// reference before ctr called
import java.io.*;
import java.lang.Override;
import java.lang.String;
import java.net.*;

class A {
 A(int i) {}
 A(A a) {}
 int f() { return 0; }
 int fi;
} 	

class B extends A {
  int bi;
  B(int h) {
    super(<error descr="Cannot reference 'B.bi' before supertype constructor has been called">bi</error>);
  }
  B() {
    this(<error descr="Cannot reference 'B.bi' before supertype constructor has been called">bi</error>);
  }

  B(String s) {
    super(<error descr="Cannot reference 'B.db' before supertype constructor has been called">db</error>(1) );
  }

  B(int i, int j) {
    super(<error descr="Cannot reference 'A.f' before supertype constructor has been called">f</error>());
  }
  B(int i, int j, int k) {
    super(<error descr="Cannot reference 'A.f' before supertype constructor has been called">super.f</error>());
  }

  B(String s, int i) {
    super(s.length());
  }

  B(int s, int i, char j) {
    super(<error descr="Cannot reference 'A.fi' before supertype constructor has been called">super.fi</error> );
  }

  B(double d) {
    super(new <error descr="Cannot reference 'Inner' before supertype constructor has been called">Inner</error>() );
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
      super(<error descr="Cannot reference 'Bb.this' before supertype constructor has been called">Enc.Bb.this</error>.ibb );
    }

    Bb(int i, String s) {
      super(Enc.this.ienc);
    }

    Bb(int i, char j) {
      super(<error descr="Cannot reference 'this' before supertype constructor has been called">this</error> );
    }
  }

  Enc() {
    this(new <error descr="Cannot reference 'Bb' before supertype constructor has been called">Bb</error>());
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
               <error descr="Cannot reference 'YellinBug.this' before supertype constructor has been called">YellinBug.this</error>.f();
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
      <error descr="Cannot reference 'UseIt.o' before supertype constructor has been called">o</error>.super();
    }

    Outer geto() {
     return null;
    }
    UseIt(int x) {
      <error descr="Cannot reference 'UseIt.geto' before supertype constructor has been called">geto</error>().super();
    }
    UseIt(Outer x) {
      <error descr="Cannot reference 'this' before supertype constructor has been called">this</error>.super();
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
            AFoo afoo = new <error descr="Cannot reference 'AFoo' before supertype constructor has been called">AFoo</error>();
            return super.toString();
          }
        });
      }

      Child(String s, Foo foo) {
        super(s, new <error descr="Cannot reference 'AFoo' before supertype constructor has been called">AFoo</error>());
      }

      class AFoo extends Foo {} 
    }
}