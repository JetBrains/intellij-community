
import java.util.function.Function;

class Example {
  void test() {
    Function<? super Integer, Float> firstFunction = null;
    Function<Float, String> secondFunction = null;
    Function<? super Integer, String> function = firstFunction.andThen(secondFunction);
  }
}