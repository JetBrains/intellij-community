// unhandled exceptions from superclases/etc
import java.io.*;

class a  {
 a(int i) {}
}

// super ctr

<error descr="There is no default constructor available in 'a'">class b extends a</error> {
}

class c extends a {
  <error descr="There is no default constructor available in 'a'">c()</error> {
  }

  c(String s) {
    this(1);
  }
  c(int i) {
    super(i);
  }
}

class A {
    private A() {}
    class B extends A {}
}

class A1 {
    A1() throws Exception {}
}

<error descr="Unhandled exception: java.lang.Exception">class B1 extends A1</error>
{}

class A2 extends A1 {
  <error descr="Unhandled exception: java.lang.Exception">A2()</error> {}
}


// exception thrown from within anonymous
class A3 {
    void f() throws Exception {
        new A3() {
            int g() throws Exception {
                return 0;
            }
            int k=g();
        };
    }
}


// in initializer
class Test{
  final String s = <error descr="Unhandled exception: java.lang.Exception">makeString</error>();
  String makeString() throws Exception {throw new Exception();}
}


class C1 {
  public C1() throws IllegalArgumentException {}
}
class C2 extends C1 {
  public C2() {
  }
}

// private but accessible base ctr
class D1 {
    private D1() {}
    static class D2 extends D1 {
        D2() {
            System.out.println("!");
        }
    }

    public static void main(String[] args) {
        new D2();
        new D1();
    }
}


///////////////
class MyClass
{
    public MyClass() throws Exception
    {
        //default ctor throws exc
    }
    
    public MyClass(Object anObject)
    {
        //other ctor does not
    }
}

class MyClass2 extends MyClass 
{
    //HERE good code is marked red
    public MyClass2 (Object anObject)
    {
        super(anObject);
    }
}

class ThrowCC  {
    public void test3() throws Exception {
      Throwable exception = null;
      <error descr="Unhandled exception: java.lang.Throwable">throw exception;</error>
    }
}


//Inaccessible field
class J {
    int t = new I1().i; //access object class is OK
    int v = new I2().<error descr="'i' has private access in 'J.I1'">i</error>; //bad access object class

    class I1 {
        class I3 {
            int t = i; //visibility OK
        }

        private int i;
    }

    class I2 extends I1 {
        int j = <error descr="'i' has private access in 'J.I1'">i</error>; //We don't see i from baser class
    }

    static I1 i1;
    class I4 {
        int u = i1.i; //OK, i is visible since the toplevel class is the same
    }
}
class Test3 {

    private class Child extends Parent {
    }

    private class Parent extends SuperParent {
    }

    private class SuperParent {
        private int field = 1;
    }

    public void foo() {
        Child child = new Child();
        int i = child.<error descr="'field' has private access in 'Test3.SuperParent'">field</error>;
    }
}

//IDEADEV-4455: this code is OK
class XYZ {
    private class Inner extends XYZ {
        private final String s;
        Inner(String _s) {
            this.s = _s;
        }


        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Inner y = (Inner) o;

            if (s != null ? !s.equals(y.s) : y.s != null) return false;

            return true;
        }
    }
}
//end of IDEADEV-4455