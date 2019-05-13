// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.*;

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
    // toUnmodifiableSet will not preserve order; not suggested here
    return Collections.unmodifiableSet(result);
  }

  Map<String, Integer> map(List<String> input) {
    Map<String, Integer> result = new HashMap<>(100);
    for (String s : input) {
      if (!s.isEmpty()) {
        result.put(s, s.length());
      }
    }
    return Collections.unmodifiableMap(result);
  }

  Map<String, Integer> map1(List<String> input) {
    Map<String, Integer> result = new TreeMap<>();
    for (String s : input) {
      if (!s.isEmpty()) {
        result.put(s, s.length());
      }
    }
    return Collections.unmodifiableMap(result);
  }

  Map<String, Integer> map2(int[] input) {
    Map<String, Integer> result = new HashMap<>();
    for (int s : input) {
      if (s > 0) {
        result.put(String.valueOf(s), s);
      }
    }
    return Collections.unmodifiableMap(result);
  }
}