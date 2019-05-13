package test;

import static test.a.A.*;
import static test.b.B.*;

class Test {
  public static void main(String[] args) {
    System.out.println(A_CONST);
    System.out.println(B_CONST);
    System.out.println(SHARED_CONST);
  }
}

class A {
  public static final int A_CONST = 1;
  public static final int SHARED_CONST = 2;
}

class B {
  public static final int B_CONST = 1;
  private static final int SHARED_CONST = 2;
}
