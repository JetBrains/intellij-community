// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Test {
  void testList(List<String> input) {
    List<String> result = new ArrayList<>(10);
    for (String s : in<caret>put) {
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    System.out.println(result);

    ArrayList<String> result2 = new ArrayList<>(20);
    for (String s : input) {
      if (!s.isEmpty()) {
        result2.add(s);
      }
    }
    System.out.println(result2);

    // Non-empty
    ArrayList<String> result3 = new ArrayList<>(input);
    for (String s : input) {
      if (!s.isEmpty()) {
        result3.add(s);
      }
    }
    System.out.println(result3);

    // Non-final var used in initializer
    int size = 5;
    if(size < input.size()) size = input.size();
    List<String> result4 = new ArrayList<>(size);
    for (String s : input) {
      if(!s.isEmpty()) {
        result4.add(s);
      }
    }
    System.out.println(result4);
  }

  void testSet(List<String> input) {
    Set<String> result = new HashSet<>(10);
    for (String s : input) {
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    System.out.println(result);

    Collection<String> result2 = new LinkedHashSet<>(20, 0.8f);
    for (String s : input) {
      if (!s.isEmpty()) {
        result2.add(s);
      }
    }
    System.out.println(result2);

    // Non-empty
    AbstractSet<String> result3 = new HashSet<>(input);
    for (String s : input) {
      if (!s.isEmpty()) {
        result3.add(s);
      }
    }
    System.out.println(result3);

    Collection<TimeUnit> result4 = EnumSet.noneOf(TimeUnit.class);
    for (String s : input) {
      if (!s.isEmpty()) {
        result4.add(TimeUnit.valueOf(s));
      }
    }
    System.out.println(result4);
  }

  void testMap(List<String> input) {
    Map<Integer, String> result = new HashMap<>(10);
    for (String s : input) {
      if (!s.isEmpty()) {
        result.put(s.length(), s);
      }
    }
    System.out.println(result);

    Map<Integer, String> result2 = new HashMap<>(10, 0.8f);
    for (String s : input) {
      if (!s.isEmpty()) {
        result2.put(s.length(), s);
      }
    }
    System.out.println(result2);

    EnumMap<TimeUnit, String> result3 = new EnumMap<>(TimeUnit.class);
    for (String s : input) {
      if (!s.isEmpty()) {
        result3.put(TimeUnit.valueOf(s), s);
      }
    }
    System.out.println(result3);

    // Non-empty
    EnumMap<TimeUnit, String> result4 = new EnumMap<>(result3);
    for (String s : input) {
      if (!s.isEmpty()) {
        result4.put(TimeUnit.valueOf(s), s);
      }
    }
    System.out.println(result4);
  }
}
