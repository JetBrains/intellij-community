import java.io.*;

class Main {
    static interface A
    {
        interface B  { }
    }

    static class D implements A
    {
        private interface B { }
    }


    static class C extends D implements A
    {
        interface E extends B { }
        interface E1 extends D.B { }
        interface E2 extends A.B { }
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
        interface E extends <error descr="Reference to 'B' is ambiguous, both 'Main1.D.B' and 'Main1.A.B' match">B</error> { }
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

    interface F extends <error descr="Reference to 'B1' is ambiguous, both 'D.B1' and 'A.B1' match">B1</error> { }
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

