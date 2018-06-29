// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.*;
import java.util.stream.Collectors;

class Test {
  List<String> test(String[] list) {
      return Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableList());
  }

  Collection<String> test2(String[] list) {
      return Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
  }

  List<String> test3(String[] array) {
      return Arrays.stream(array).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toUnmodifiableList());
  }

  Set<String> test4(String[] array) {
    Set<String> result = Arrays.stream(array).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
      // toUnmodifiableSet will not preserve order; not suggested here
    return Collections.unmodifiableSet(result);
  }

  Map<String, Integer> map(List<String> input) {
      return input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableMap(s -> s, s -> s.length(), (a, b) -> b));
  }

  Map<String, Integer> map1(List<String> input) {
    Map<String, Integer> result = input.stream().filter(s -> !s.isEmpty()).collect(Collectors.toMap(s -> s, String::length, (a, b) -> b, TreeMap::new));
      return Collections.unmodifiableMap(result);
  }

  Map<String, Integer> map2(int[] input) {
      return Arrays.stream(input).filter(s -> s > 0).boxed().collect(Collectors.toUnmodifiableMap(s -> String.valueOf(s), s -> s, (a, b) -> b));
  }
}