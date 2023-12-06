// "Create method 'nextFn'" "true-preview"
import java.util.function.Function;

class X {
  void x() {
    Function<Double, Character> fn1 = d -> String.valueOf(d)
      .charAt(0);
    Function<Double, Integer> fn = fn1.andThen(<caret>nextFn());
  }
}