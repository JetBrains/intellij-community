import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;


abstract class Example {
  interface PriceList {}
 
  List<PriceList> findByTags(List<String> tags, Date date) {
    return copyOf(findMatching(tags).stream().filter(isActive(date)).collect(toList()));
  }

  protected abstract Predicate<PriceList> isActive(Date date);
  protected abstract List<PriceList> findMatching(List<String> tags);
  protected abstract <E> List<E> copyOf(Collection<? extends E> e);
}