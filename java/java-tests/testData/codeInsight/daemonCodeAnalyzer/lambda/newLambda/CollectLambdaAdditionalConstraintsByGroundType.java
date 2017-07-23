import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {

  public void main(Stream<Entry<String, Object>> stream) {
    supp(() -> stream.collect(Collectors. toMap(Entry::getKey, Entry::getValue)));
    supp(() -> stream.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));
  }

  void supp(Supplier<? extends Map<String, Object>> s) {}
}