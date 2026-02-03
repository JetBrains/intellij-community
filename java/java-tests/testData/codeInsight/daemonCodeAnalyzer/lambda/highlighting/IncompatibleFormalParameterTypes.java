class LambdaTest {

  public void highlightsTheBug(Stream<String> stream) {
    stream.flatMap((Block<? super String> sink, String element) -> {});
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
