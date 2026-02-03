// "Replace method reference with lambda" "true-preview"
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collector;

public class Main {
  interface Index {
    int asInteger();
  }
  interface IndexSet<S extends Index> {
    List<S> asList();
  }

  public static OptionalInt min(IndexSet<?> set) {
    return set.asList()
      .stream()
      .mapToInt(o -> o.asInteger())
      .min();
  }
}
