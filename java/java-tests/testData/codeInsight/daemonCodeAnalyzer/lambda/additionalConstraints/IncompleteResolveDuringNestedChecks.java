import java.util.Optional;
import java.util.function.UnaryOperator;

class Test<T> {
  private void example() {
    update(x -> x.flatMap<error descr="Expected no arguments but found 1">(y -> getEmpty())</error>);
  }

  private <J> Test<J> flatMap() {
    return null;
  }

  private <K> Optional<K> getEmpty() {
    return Optional.empty();
  }

  void update(UnaryOperator<Test<Integer>> u) {}
}