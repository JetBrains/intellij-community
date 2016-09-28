
class MultipleInheritance {
  interface A {
    int X = 1;
  }

  interface B extends A {
    int X = 2;
  }

  interface C extends A, B {
    int Y = C.<error descr="Reference to 'X' is ambiguous, both 'A.X' and 'B.X' match">X</error>;
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