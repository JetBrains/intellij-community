import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

public class FilterPredicate {
  public FluentIterable<String> filterString(FluentIterable<String> p1, Predicate<String> p2) {
    return p1.filter(p2::apply);
  }
}
