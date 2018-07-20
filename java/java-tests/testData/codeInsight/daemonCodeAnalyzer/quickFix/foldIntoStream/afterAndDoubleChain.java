import java.util.stream.IntStream;

// "Fold expression into Stream chain" "true"
class Test {
  boolean foo(double[] arr) {
    return IntStream.of(1, 3, 7, 9).allMatch(i -> arr[i] >= 5);
  }
}