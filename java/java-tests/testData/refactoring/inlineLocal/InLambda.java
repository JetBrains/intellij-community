import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class InlineTest {
  public List<Object> foo(Collection<Object> bars, Predicate<String> filter) {
    return bars.stream().filter(bar -> {
      final String se<caret>archString = bar.toString();
      return filter.test(searchString);
    }).collect(Collectors.toList());
  }
}
