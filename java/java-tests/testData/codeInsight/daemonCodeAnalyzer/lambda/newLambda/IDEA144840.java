
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

class Test {
  private Map<Pair<String, Number>, BiFunction<Number, Object, BigDecimal>> spec = null;

  public Optional<BigDecimal> fooIn2(Map<String, Optional<Object>> values) {
    return values.entrySet().stream().map(entry -> {
      String pathName = entry.getKey();
      return entry.getValue().flatMap(value ->
                                        spec.entrySet().stream().filter(specEntry ->
                                                                          pathName.equals(specEntry.getKey().getLeft())).findFirst().map(specEntry -> {
                                          Pair<String, Number> specEntryKey = specEntry.getKey();
                                          Number path = specEntryKey.getRight();
                                          BiFunction<Number, Object, BigDecimal> compareFunction = specEntry.getValue();
                                          return compareFunction.apply(path, value);
                                        }));
    }).flatMap(option -> option.map(Stream::of).orElse(Stream.empty())).reduce(BigDecimal::add);
  }

  static class Pair<A, B> {
    public A getLeft() {
      return null;
    }

    public B getRight() {
      return null;
    }
  }
}
