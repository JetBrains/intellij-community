import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CollectList {
  public <T> List<T> toList(Stream<T> p) { return p.collect(Collectors.toList()); }
  public <T> List<T> toSortedList(Stream<T> p, Comparator<T> c) { return p.sorted(c).collect(Collectors.toList()); }
  public <T> Set<T> toSet(Stream<T> p) { return p.collect(Collectors.toSet()); }
  public <T> Set<T> toSortedSet(Stream<T> p, Comparator<T> c) { return p.collect(Collectors.toCollection(() -> new TreeSet<>(c))); }
  public <T> Map<T, T> toMap(Stream<T> p) { return p.collect(Collectors.toMap(Function.identity(), input -> input)); }

  void m(Stream<String> fluentIterable) {
    Map<String, String> map = fluentIterable.collect(Collectors.toMap(Function.identity(), input -> input));
    System.out.println(map.size());
    System.out.println(map.hashCode());
  }
}