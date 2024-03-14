abstract class PertinentToApplicabilityOfExplicitlyTypedLambdaTest {

  interface A {
    B m(int a);
  }

  interface B {
    int m(int b);
  }

  abstract void foo(A a);
  abstract void foo(B b);

  {
    <error descr="Ambiguous method call: both 'PertinentToApplicabilityOfExplicitlyTypedLambdaTest.foo(A)' and 'PertinentToApplicabilityOfExplicitlyTypedLambdaTest.foo(B)' match">foo</error>(<warning descr="Parameter 'x' is never used">x</warning> -> <warning descr="Parameter 'y' is never used">y</warning> -> 42);
  }
}
