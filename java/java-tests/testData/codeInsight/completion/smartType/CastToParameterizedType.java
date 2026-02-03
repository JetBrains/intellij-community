public class A<T> {
    private static final A<?> X = null;

    A<String> get(B o) {
      return (<caret>X;
    }
  }

class B {

}