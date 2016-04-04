import java.util.Optional;

class Resource<<warning descr="Type parameter 'K' is never used">K</warning>> {
  private static <T> Resource<T> <warning descr="Private method 'of(T)' is never used">of</warning>(T <warning descr="Parameter 'data' is never used">data</warning>) {
    return null;
  }

  private static <T> Resource<T> of(Optional<T> <warning descr="Parameter 'i' is never used">i</warning>) {
    return null;
  }

  static {
    final Optional<String> empty = Optional.empty();
    Resource.of(empty.flatMap(s -> empty));
  }
}