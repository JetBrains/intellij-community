// illegal method calls

class A {
    private class B {
      Object o = super<error descr="'.' expected">;</error>
    }
    private B b = null;
    A(A a)
    {
        new A(a).new B();
        B b = new A(a).b;
        AA aa = (AA) a;
        AA.SAA saa = aa.new SAA();
        AA.SAA saa1 = <error descr="'AA' is not an enclosing class">new AA.SAA()</error>;
    }
}

class AA extends A {
    private AA aa;
    AA(A a) {
        super(a);
    }
    class SAA {}

    void f() {
      new AA.SAA();
      new SAA();
      AA.this.aa.new SAA();

      class MyAA extends AA {
        public MyAA(A a) {
          super(a);
        }
      }
    }
}

class AX {
    class B {
    }
}
class CX {
    {
        <error descr="'AX' is not an enclosing class">new AX.B()</error>;
    }
}



class c {
  c() {

  }
    class inner {
        class ininner {}
    }


    static void f() {
        <error descr="'c.this' cannot be referenced from a static context">new inner()</error>;
    }

    static {
        <error descr="'c.this' cannot be referenced from a static context">new inner()</error>;
    }
}


class A1 {
  void f() {}
}
class B1 {
  void f() {
    A1.<error descr="Non-static method 'f()' cannot be referenced from a static context">f</error>();
  }
}

class AAAA implements java.io.Serializable
{
    public AAAA ()
    {
        super(); // here
    }
}

class DCC {
    public DCC(int i) {
    }

    public DCC(int i, int z) {
       <error descr="Method call expected">DCC(i)</error>;
    }
    void f() {
        <error descr="Method call expected">DCC(1)</error>;
        new DCC(1);
    }
}

class ThisExpression {
    static String foo() {
        System.out.println(<error descr="'ThisExpression.this' cannot be referenced from a static context">this</error>);
        return <error descr="'ThisExpression.this' cannot be referenced from a static context">this</error>.toString(); 
    }
}
