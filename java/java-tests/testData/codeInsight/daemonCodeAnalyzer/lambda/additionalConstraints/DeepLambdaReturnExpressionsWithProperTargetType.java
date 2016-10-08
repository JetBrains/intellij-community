
import java.util.Optional;
import java.util.function.UnaryOperator;

class Test {

  private void example() {
    update(x -> x.flatMap(y -> Optional.empty()));
    update(x -> x.flatMap(y -> x.flatMap(z -> Optional.empty())));
    update(x -> x.flatMap(y -> x.flatMap(z -> x.flatMap(w -> Optional.empty()))));

    update(x -> {
      return x.flatMap(y -> {
        return x.flatMap(z -> {
          return x.flatMap(w -> {
            return Optional.empty();
          });
        });
      });
    });
  }

  void update(UnaryOperator<Optional<Integer>> u) {}
}