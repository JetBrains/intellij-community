import java.util.function.Predicate;
import java.util.stream.Stream;

public class FilterPredicate {
  public Stream<String> filterString(Stream<String> p1, Predicate<String> p2) {
    return p1.filter(p2);
  }
}
