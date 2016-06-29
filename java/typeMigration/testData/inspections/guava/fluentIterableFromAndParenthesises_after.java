import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Main {
  void m(Object input, List<Object> transformed) {
    transformed.addAll(StreamSupport.stream(((Iterable<Object>) input).spliterator(), false).collect(Collectors.toList()));
  }
}