// final Fields initialization
import java.io.*;
import java.net.*;
import java.awt.event.*;

public class a  {
  /**
   * javadoc should not be highlighted
   */ 
  <error descr="Variable 'javaDoced' might not have been initialized">final int javaDoced</error>;

  <error descr="Variable 'sfi1' might not have been initialized">static final int sfi1</error>;
  <error descr="Variable 'sfi2' might not have been initialized">static final int sfi2</error>;
  <error descr="Variable 'fi1' might not have been initialized">final int fi1</error>;
  <error descr="Variable 'fi2' might not have been initialized">final int fi2</error>;

  class inner {
    <error descr="Variable 'fii' might not have been initialized">final int fii</error>;
  }
  final int fi3;
  final int fi4;


  static final int csfi1 = 0;
  static final int csfi2 = csfi1 + 13 / 5;
  static final int csfi3; 
  static {
   if (csfi1 < 13) {
     csfi3 = csfi2 + (csfi1 == 4 ? 5 : csfi2/13);
   }
   else {
     csfi3 = 0;
     sfi2 = 2;
   }
  }


  final int cifi1 = 1;
  final int cifi2 = ff();
  final int cifi3;

  int ff() { 
    int i = cifi1 + cifi2 + cifi3 + fi3 + fi4;
    return i; 
  }

  {
   switch (cifi1) {
    case 2: cifi3 = 2; break;
    case 1: cifi3 = 20; break;
    case 0: cifi3 = 21; break;
    default: cifi3 = 22; break;
   }
  }

  final int cifi4;
  a() {
    cifi4 = 4;
    int i = <error descr="Variable 'fi3' might not have been initialized">fi3</error>;
    fi3 = 3;
    a ainst = null;
    fi4 = ainst.fi4;
  }
  a(int i) {
    this.cifi4 = i;
    fi2 = 3;
    fi3 = 3;
    a ainst = null;
    fi4 = ainst.fi4;
  }
  a(String s) {
    this(0);
    int i = fi3;

  }
}

class Test {
  // access from within inner class OK
    final boolean FB;
    {
      class s {
        boolean b = FB;
      }
      FB = true;

    }
    private final String text;
    public Test() {
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doSomething(text);////
        }
      };
      text = "Hello world";
    }

    private void doSomething(String value) {
    }
}

// multiple initalizers
class c1 {
 private final String x;
 {
   x = "Hello";
 }

 private final String y;
 {
   y = x; 
 }
 void f() {
   String s = x+y;
 }
}

class c2 {
    static { 
    }
    private static final int limit;
    static {
        limit = 5;
    }
    final String s;
    int k = <error descr="Variable 's' might not have been initialized">s</error>.length();
    final String c;
    int k2 = <error descr="Variable 'c' might not have been initialized">c</error>.length();
    {
        s = "";
    }

    public c2() {
        c = "";
    }
    // its ok
    int k3 = c.length();

    c2(int i) {
      this();
    }
}

class UninitializedFinal2 {
    <error descr="Variable 's' might not have been initialized">private final String s</error>;

    UninitializedFinal2(){
        try {
        }
        finally {
        }
    }
}
class UninitedFinalFied {

	<error descr="Variable 'string' might not have been initialized">private final String string</error>;

	public UninitedFinalFied() throws IOException {
		init();
	}

	private void init() throws IOException {}
}
class AssertFinalFied {
	private final String string;

	public AssertFinalFied(boolean b) throws Exception {
	     assert b;
	     string = null;
	}
}

class a20Exotic {
    int n=k=0;
    final int k;
    final int k2;
    int n2 = k==0 ? (k2=9) : (k2=0);
}

public class cX {
    final int i;
    cX() {
        this(1);
        int k = i;
    }
    cX(int d) {
        i = d;
    }
}

// http://www.intellij.net/tracker/idea/viewSCR?publicId=20097
class Lemma {
    public Lemma() {
        name.hashCode();
    }
    private final String name;
    {   name = "Andy";
    }
}

class correct {
    void f() {
        final Object o;
        o = new Object();
        new Object() {
          { o.toString(); }
        }; 
    }
}

public class X {
    final int i;
    X() {
        try {
            i = 0;
        }
        finally {

        }
    }
}


