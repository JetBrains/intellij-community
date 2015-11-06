
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Stream;

class Bug {

  public static void main(String[] args) {
    Stream<String> tokenStream = Stream.of("hello", "world");

    Comparator<String> order = Comparator.naturalOrder();
    Min<String> min = tokenStream.collect(
      () -> new Min<>(order),
      Min::accept,
      Min::combine);

    System.out.printf("Min = %s%n",
                      min.asOptional().orElse(null));
  }

  static class Min<T> implements Consumer<T> {

    private final BinaryOperator<T> minOf;
    private T min;

    public Min(Comparator<? super T> order) {
      this.minOf = BinaryOperator.minBy(order);
    }

    @Override
    public void accept(T t) {
      this.min = min != null ? minOf.apply(min, t) : t;
    }

    public void combine(Min<T> other) {
      if (other.min != null) {
        accept(other.min);
      }
    }

    public Optional<T> asOptional() {
      return Optional.ofNullable(min);
    }
  }
}
