import java.io.*;

class Main {
    static class Outer {
        static interface A {
            interface B {}
        }

        static class D implements A {
            private interface B {}
        }


        static class C extends D implements A {
            interface E extends B {}
            interface E1 extends D.B {}
            interface E2 extends A.B {}
        }
    }
    static class E {
        {
            D d = new D() {
                void foo(<error descr="'D.B' has private access in 'D'">B</error> b){

                }
            };
        }
    }
}



class Main1 {
    static interface A
    {
        interface B  { }
    }

    static class D implements A
    {
        interface B { }
    }


    static class C extends D implements A
    {
        interface E extends <error descr="Reference to 'B' is ambiguous, both 'Main1.A.B' and 'Main1.D.B' match">B</error> { }
        interface E1 extends D.B {
        }
        interface E2 extends A.B { }
    }

}


interface A
{
    interface B  { }
    interface B1  { }
}

class D implements A
{
    private interface B { }
    interface B1 { }
}


class C extends D implements A
{
    interface E extends B { }
    interface E1 extends D.<error descr="'D.B' has private access in 'D'">B</error> { }
    interface E2 extends A.B { }

    interface F extends <error descr="Reference to 'B1' is ambiguous, both 'A.B1' and 'D.B1' match">B1</error> { }
    interface F1 extends D.B1 { }
    interface F2 extends A.B1 { }

}


class AO {}
class BAO {
    AO bar = new AO();
    {
        bar.foo();
    }
    private class AO {
        void foo(){}
    }

}

class WithFileInputStream {
  private static final Runnable runn = new Runnable() {
    public void run() {
      new FileInputStream("path");
    }
  };
  
  private static class FileInputStream {
    private FileInputStream(String str) {
    }
  }
}

class ContainingKlass {
    public static class Inner {
    }

    private static class OuterInner {
        private static final class Inner {
            private Inner s() {
                return this;
            }
        }
    }
}
class Q {
  class A {
    class X  {
    }
  }
  class B extends A {
    class X extends B {
      Class c = X.class;
      //           ^ IntelliJ: Reference to 'X' is ambiguous, both 'Q.B.X' and 'Q.A.X' match
      //           ^ javac   : no problem!
    }
  }
}
abstract class Base {
  public class Type{}

  static abstract class Derived extends Base {
    public class Type{}

    /**
     * {@link Type} should resolve to Derived.Type
     */
    static class Concrete extends Derived {
      Type t;
    }
  }
}
interface Base2 {
  public class Type{}

  interface Derived extends Base2 {
    public class Type{}

    static class Concrete implements Derived {
      // still incorrect error here, not in fact ambiguous:
      <error descr="Reference to 'Type' is ambiguous, both 'Base2.Derived.Type' and 'Base2.Type' match">Type</error> t;
    }
  }
}
interface I1 {
  class Z {}
}
interface I2 extends I1 {
  class Z {}
}
interface I3 extends I1 {
  Z good();
}
interface I4 extends I2, I3 {
  Z ambiguous(); // there should be an error here
}
interface I5 extends I4 {
  Z ambiguous(); // there should be an error here
}

