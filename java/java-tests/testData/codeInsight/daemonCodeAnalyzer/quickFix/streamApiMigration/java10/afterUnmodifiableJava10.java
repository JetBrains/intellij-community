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
}