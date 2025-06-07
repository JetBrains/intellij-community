import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class Test {
  void m() {
    // IDEA-337371 -- error reporting still should be improved
    var o = new MapDropdownChoice<String, Integer>(
      () -> {
        Map<String, Integer> choices = Map.of("id1", 1);
        return choices.entrySet().stream()
          .collect(Collectors.toMap(<error descr="Non-static method cannot be referenced from a static context">Map.Entry::getKey</error>, <error descr="Non-static method cannot be referenced from a static context">Map.Entry::getValue</error>,
                                    (e1, e2) -> e1, LinkedHashMap::new));
      });
  }
  static class MapDropdownChoice<K, V> {
    public MapDropdownChoice(Supplier<? extends Map<K, ? extends V>> choiceMap) {}
  }
}