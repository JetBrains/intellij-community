class SampleExtendsWildcard {
  public void highlightsTheBug(Stream<String> stream) {
    stream.flatMap((Block<?> sink, String element) -> {});
  }

  public interface Block<B> {
    void apply(B t);
  }

  public interface Stream<S> {
    <R> Stream<R> flatMap(FlatMapper<? super S, R> mapper);

  }

  public interface FlatMapper<F, R> {
    void flatMapInto(Block<? extends R> sink, F element);
  }
}

class SampleSuperWildcard {
  public void highlightsTheBug(Stream<String> stream) {
    stream.flatMap(<error descr="Cannot infer functional interface type">(Block<?> sink, String element) -> {}</error>);
  }

  public interface Block<B> {
    void apply(B t);
  }

  public interface Stream<S> {
    <R> Stream<R> flatMap(FlatMapper<? super S, R> mapper);

  }

  public interface FlatMapper<F, R> {
    void flatMapInto(Block<? super R> sink, F element);
  }
}
