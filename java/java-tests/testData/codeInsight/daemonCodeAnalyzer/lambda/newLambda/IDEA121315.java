import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IDEA121315 {
  class Issue {
    Long getId() {
      return 1l;
    }
  }

  <T> T id(T i) {
    return i;
  }

  void foo(Stream<Issue> map){
    Map<Long, Issue> id2Issue = map.collect(Collectors.toMap(null, p -> id(p)));
    Map<Long, Issue> id2Issue1 = map.collect(Collectors.toMap(null, p -> p));
    Map<Long, Issue> id2Issue2 = map.collect(Collectors.toMap(null, this::id));

  }
}
