// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.*;

// Java 8 language level used: no toUnmodifiable suggestions
class Test {
  List<String> test(String[] list) {
    List<String> result = new LinkedList<>();
    f<caret>or (int i = 0; i < list.length; i++) {
      String s = list[i];
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    return Collections.unmodifiableList(result);
  }

  Collection<String> test2(String[] list) {
    Set<String> result = new HashSet<>();
    for (int i = 0; i < list.length; i++) {
      String s = list[i];
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    return Collections.unmodifiableCollection(result);
  }

  List<String> test3(String[] array) {
    Set<String> result = new HashSet<>();
    for (int i = 0; i < array.length; i++) {
      String s = array[i];
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    List<String> list = new ArrayList<>(result);
    return Collections.unmodifiableList(list);
  }

  Set<String> test4(String[] array) {
    Set<String> result = new TreeSet<>();
    for (int i = 0; i < array.length; i++) {
      String s = array[i];
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    return Collections.unmodifiableSet(result);
  }
}