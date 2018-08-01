// "Create type parameter 'U'" "true"

public class Helpers {

  interface RunAlgebra<A, R> {

  }

  interface ReturnAlgebra<R, O> {

  }

  interface SomeAlgebra<R> {

  }

  interface AlgebraImplementation extends ReturnAlgebra<String, Integer>, SomeAlgebra<String> {

  }

  public static <R> void giveMeAlgebraImplementation(RunAlgebra<AlgebraImplementation, R> algebra) {

  }

  public static <R, U, A extends ReturnAlgebra<R, U>> RunAlgebra<A, U> aReturn() {
    return null;
  }

  public static <R, U, A extends ReturnAlgebra<R, U>> RunAlgebra<A, R> aReturn(String overload) {
    return null;
  }

  public static <R,
          A extends ReturnAlgebra<R, U>,
          AI extends SomeAlgebra<R>, U> RunAlgebra<A, R> consumeReturn(A returnAlgebra) {
    return null;
  }


}