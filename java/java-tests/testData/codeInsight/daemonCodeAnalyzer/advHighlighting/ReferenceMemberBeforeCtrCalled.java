// reference before ctr called
import java.io.*;
import java.net.*;

class a {
 a(int i) {}
 a(a a) {}
 int f() { return 0; }
 int fi;
} 	

class b extends a {
  int bi;
  b(int h) {
    super(<error descr="Cannot reference 'b.bi' before supertype constructor has been called">bi</error>);
  }
  b() {
    this(<error descr="Cannot reference 'b.bi' before supertype constructor has been called">bi</error>);
  }

  b(String s) {
    super(<error descr="Cannot reference 'b.db' before supertype constructor has been called">db</error>(1) );
  }

  b(int i, int j) {
    super(<error descr="Cannot reference 'a.f' before supertype constructor has been called">f</error>());
  }
  b(int i, int j, int k) {
    super(<error descr="Cannot reference 'a.f' before supertype constructor has been called">super.f</error>());
  }

  b(String s, int i) {
    super(s.length());
  }

  b(int s, int i, char j) {
    super(<error descr="Cannot reference 'a.fi' before supertype constructor has been called">super.fi</error> );
  }

  b(double d) {
    super(new <error descr="Cannot reference 'inner' before supertype constructor has been called">inner</error>() );
  }
  class inner extends a {inner(){super(1);}}

  int db(int j) { 
   return 0;
  }
}


class enc {
  int ienc;
  class bb extends a {
    int ibb;
    bb() { super(ienc); }
    bb(int i) {
      super(i);
    }

    bb(int i, int j) {
      super(<error descr="Cannot reference 'bb.this' before supertype constructor has been called">enc.bb.this</error>.ibb );
    }

    bb(int i, String s) {
      super(enc.this.ienc);
    }

    bb(int i, char j) {
      super(<error descr="Cannot reference 'this' before supertype constructor has been called">this</error> );
    }


  }

  enc() {
    this(new <error descr="Cannot reference 'bb' before supertype constructor has been called">bb</error>());
  }
  enc(bb b) {}
}

// static are OK
class c2 extends a {
  static final int fi = 4;
  c2() {
    super(fi);
  }
  c2(int i) {
    super(sf());
  }
  static int sf() { return 0; }

  c2(int i, int j) {
    super(new sc().i);
  }
  static class sc {
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
