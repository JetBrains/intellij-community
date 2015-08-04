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
    <error descr="Cannot resolve method 'foo(<lambda expression>)'">foo</error>(x -> y -> 42);
  }
}
