// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Test {
  void testList(List<String> input) {
      List<String> result = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toCollection(() -> new ArrayList<>(10)));
      System.out.println(result);

      ArrayList<String> result2 = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toCollection(() -> new ArrayList<>(20)));
      System.out.println(result2);

    // Non-empty
    ArrayList<String> result3 = new ArrayList<>(input);
      input.stream().filter(s -> !s.isEmpty()).forEach(result3::add);
    System.out.println(result3);

    // Non-final var used in initializer
    int size = 5;
    if(size < input.size()) size = input.size();
    List<String> result4 = new ArrayList<>(size);
      input.stream().filter(s -> !s.isEmpty()).forEach(result4::add);
    System.out.println(result4);
  }

  void testSet(List<String> input) {
      Set<String> result = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toCollection(() -> new HashSet<>(10)));
      System.out.println(result);

      Collection<String> result2 = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toCollection(() -> new LinkedHashSet<>(20, 0.8f)));
      System.out.println(result2);

    // Non-empty
    AbstractSet<String> result3 = new HashSet<>(input);
      input.stream().filter(s -> !s.isEmpty()).forEach(result3::add);
    System.out.println(result3);

      Collection<TimeUnit> result4 = input.stream().filter(s -> !s.isEmpty()).map(TimeUnit::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(TimeUnit.class)));
      System.out.println(result4);
  }

  void testMap(List<String> input) {
      Map<Integer, String> result = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toMap(String::length, s -> s, (a, b) -> b, () -> new HashMap<>(10)));
      System.out.println(result);

      Map<Integer, String> result2 = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toMap(String::length, s -> s, (a, b) -> b, () -> new HashMap<>(10, 0.8f)));
      System.out.println(result2);

      EnumMap<TimeUnit, String> result3 = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toMap(TimeUnit::valueOf, s -> s, (a, b) -> b, () -> new EnumMap<>(TimeUnit.class)));
      System.out.println(result3);

    // Non-empty
    EnumMap<TimeUnit, String> result4 = new EnumMap<>(result3);
      input.stream().filter(s -> !s.isEmpty()).forEach(s -> result4.put(TimeUnit.valueOf(s), s));
    System.out.println(result4);
  }
}
