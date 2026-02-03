
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;


import java.util.Map;
import java.util.stream.Collector;

class FooBar {

  void p(Map<String, String> m) {
    m.entrySet().stream()
      .collect(Collector.of(() -> new HashMap<>(),
                            (a, e) -> a.put(e.getValue(), e.getKey()),
                            (l, r) -> {
                              l.put<caret>All(r);
                              return l;
                            }));
  }
}
