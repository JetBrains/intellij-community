import java.util.function.Function;

interface Repository<T> {
  <S extends T> S save(S s);
}

interface Builder<T> {

  T build();

  default <D> Builder<D> map(Function<T, D> fx) {
    return () -> fx.apply(this.build());
  }
}

class Usage {
  void test(Repository<String> repository, Builder<String> sample) {
    sample.map(repository::save).build();
    sample.map((s) -> repository.save(s)).build();
  }
}