class Test {
  sealed interface A1 permits A11, A12 {}
  sealed interface A2 permits A21, A22 {}

  <error descr="Cyclic inheritance involving 'Test.A11'">sealed interface A11 extends A11 <error descr="'implements' not allowed on interface">implements</error> A1</error> {}
  record A12() implements A1 {}

  record A21() implements A2 { }
  record A22() implements A2 { }

  record R1(A2 a2) { }
  record R2(A1 a1, R1 r1) { }

  static void r(R2 r2) {
    switch (r2) {
      case R2(A11 b1, R1(A2 b2)) -> System.out.println("1");
      case R2(A12 b1, R1(A22 b2)) -> System.out.println("2");
      case R2(A1 b1, R1(A21 b2)) -> System.out.println("3");
    }
  }
}