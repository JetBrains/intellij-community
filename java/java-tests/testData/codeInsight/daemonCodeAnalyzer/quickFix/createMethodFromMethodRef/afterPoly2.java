// "Create method 'hello'" "true"

import java.util.function.BiFunction;

class X {
  void x() {
    BiFunction<String, Integer, Double> fn = (s, i) -> ((double)s.length())/i;
    BiFunction<String, Integer, Character> fn2 = fn.andThen(X::hello);
  }

    private static Character hello(Double aDouble) {
        return null;
    }
}
