// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.*;
import java.util.stream.Collectors;

// Java 8 language level used: no toUnmodifiable suggestions
class Test {
  List<String> test(String[] list) {
    List<String> result = Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedList::new));
      return Collections.unmodifiableList(result);
  }

  Collection<String> test2(String[] list) {
    Set<String> result = Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
      return Collections.unmodifiableCollection(result);
  }

  List<String> test3(String[] array) {
      List<String> list = Arrays.stream(array).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
    return Collections.unmodifiableList(list);
  }

  Set<String> test4(String[] array) {
    Set<String> result = Arrays.stream(array).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
      return Collections.unmodifiableSet(result);
  }
}