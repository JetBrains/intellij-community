
class MultipleInheritance {
  interface A {
    int X = 1;
    String FOO = "foo";
  }

  interface B extends A {
    int X = 2;
    String FOO = "foo";
  }

  interface C extends A, B {
    int Y = C.<error descr="Reference to 'X' is ambiguous, both 'A.X' and 'B.X' match">X</error>;
    String BAR = C.<error descr="Reference to 'FOO' is ambiguous, both 'A.FOO' and 'B.FOO' match">FOO</error>.substring(1);
  }
}

class Shadowing {
  interface A {
    int X = 1;
  }

  interface B extends A {
    int X = 2;
  }

  interface C extends B {
    int Y = C.X;
  }
}

class MultipleInheritance2 {
  interface I1 {
    String X = "x";
  }
  static class Y implements I1 {
    public static final String X = "y";
  }
  
  static class Z extends Y {
    {
      System.out.println(X);
    }
  }

  static class Z1 extends Y implements I1 {
    {
      System.out.println(<error descr="Reference to 'X' is ambiguous, both 'Y.X' and 'I1.X' match">X</error>);
    }
  }
  
  interface I2 extends I1 {}
  static class Z2 extends Y implements I2 {
    {
      System.out.println(<error descr="Reference to 'X' is ambiguous, both 'Y.X' and 'I1.X' match">X</error>);
    }
  }

  static class Z3 extends Y implements Runnable, I2 {
    {
      System.out.println(<error descr="Reference to 'X' is ambiguous, both 'Y.X' and 'I1.X' match">X</error>);
    }
    
    public void run() {}
  }
}