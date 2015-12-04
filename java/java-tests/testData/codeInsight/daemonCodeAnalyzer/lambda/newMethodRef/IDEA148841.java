import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

final class Collectors {

  private Collectors() {
  }

  public static <T, K, D, A, M extends Map<K, D>> Collector<T, ?, M> groupingByFlatKeys(Function<? super T, Stream<? extends K>> classifier,
                                                                                        Supplier<M> mapFactory,
                                                                                        Collector<? super T, A, D> downstream) {
    return flatMapping(t -> classifier.apply(t).map(k -> new AbstractMap.SimpleEntry<>(k, t)),
                       groupingBy(Map.Entry::getKey, mapFactory, mapping(Map.Entry::getValue, downstream)));
  }

  public static <T, K, U> Collector<T, ?, Map<K, U>> toMapByFlatKeys(Function<? super T, Stream<? extends K>> keyMapper,
                                                                     Function<? super T, ? extends U> valueMapper) {
    return flatMapping(t -> keyMapper.apply(t).map(k -> new AbstractMap.SimpleEntry<>(k, t)),
                       toMap(Map.Entry::getKey, valueMapper.compose(Map.Entry::getValue)));
  }

  public static <T, U, A, R> Collector<T, ?, R> flatMapping(Function<? super T, Stream<? extends U>> mapper,
                                                            Collector<? super U, A, R> downstream) {
    BiConsumer<A, ? super U> downstreamAccumulator = downstream.accumulator();
    return Collector.of(downstream.supplier(),
                        (r, t) -> mapper.apply(t).forEach(v -> downstreamAccumulator.accept(r, v)),
                        downstream.combiner(),
                        downstream.finisher(),
                        downstream.characteristics().toArray(new Collector.Characteristics[downstream.characteristics().size()]));
  }
}
