class ExplicitLambdaNoParams {
  interface I<T> {
    T a();
  }

  <F> I<F> foo(I<F> iff) { return null;}

  {
    foo(() -> foo(() -> 1)).a();
    I<Integer> a1 = foo(() -> foo(() -> 1)).a();
  }
}

class LambdaWithFormalParameterTypes {

  interface I<T> {
    T a(int p);
  }

  <F> I<F> foo(I<F> iff) { return null;}

  {
    foo((int a) -> foo((int b) -> 1)).a(0);
    I<Integer> a1 = foo((int a) -> foo((int b) -> 1)).a(0);
  }

}
