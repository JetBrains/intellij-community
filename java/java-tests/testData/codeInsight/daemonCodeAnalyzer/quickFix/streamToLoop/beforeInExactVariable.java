// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InExactVariable {
  public void testMap() {
    Object map1 = Stream.of(1, 2, 3, 4).map(String::valueOf)
      .co<caret>llect(Collectors.toMap(String::trim, Function.identity(), (a, b) -> a, HashMap::new));
    HashMap<String, String> map2 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors
                                                                                        .toMap(String::trim, Function.identity(), (a, b) -> a, HashMap::new));
    Map<String, String> map3 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors
                                                                                    .toMap(String::trim, Function.identity(), (a, b) -> a, HashMap::new));
  }

  public void testList() {
    Object list1 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors.toList());
    Iterable<String> list2 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors.toList());
    Collection<String> list3 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors.toList());
    List<String> list4 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors.toList());
    Collection<Object> list5 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors.toList());
    Collection<?> list6 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors.toList());
  }

  public void testPartition() {
    Object map1 = Stream.of(1, 2, 3, 4).map(String::valueOf)
      .collect(Collectors.partitioningBy(x -> x.length() > 1));
    Map<Boolean, List<String>> map2 = Stream.of(1, 2, 3, 4).map(String::valueOf)
      .collect(Collectors.partitioningBy((String x) -> x.length() > 1));
  }

  public void testGroupingBy() {
    Object map1 = Stream.of(1, 2, 3, 4).map(String::valueOf)
      .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.toSet()));
    TreeMap<Integer, Set<String>> map2 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors
                                                                                              .groupingBy(String::length, TreeMap::new, Collectors.toSet()));
    NavigableMap<Integer, Set<String>> map3 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors
                                                                                                   .groupingBy(String::length, TreeMap::new, Collectors.toSet()));
    SortedMap<Integer, Set<String>> map4 = Stream.of(1, 2, 3, 4).map(String::valueOf).collect(Collectors
                                                                                                .groupingBy(String::length, TreeMap::new, Collectors.toSet()));
    Cloneable map5 = Stream.of(1, 2, 3, 4).map(String::valueOf)
      .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.toSet()));
  }
}
