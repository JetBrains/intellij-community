import java.util.function.Consumer;
class Pipeline<I, O> implements Consumer<I> {
  @Override public final void accept(I input) {}

  public <K> void then(Pipeline<O, K> pipeline, final Observable<O> observable) {
    observable.subscribe(pipeline::accept);
  }
}

interface Observable<T> {
  void subscribe(Consumer<T> x);
}