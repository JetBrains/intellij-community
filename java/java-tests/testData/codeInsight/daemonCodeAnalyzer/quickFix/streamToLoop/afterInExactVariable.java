// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InExactVariable {
  public void testMap() {
      HashMap<String, String> map = new HashMap<>();
      for (Integer i1 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf = String.valueOf(i1);
          map.putIfAbsent(valueOf.trim(), valueOf);
      }
      Object map1 = map;
      HashMap<String, String> map2 = new HashMap<>();
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String string = String.valueOf(integer);
          map2.putIfAbsent(string.trim(), string);
      }
      Map<String, String> map3 = new HashMap<>();
      for (Integer i : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(i);
          map3.putIfAbsent(s.trim(), s);
      }
  }

  public void testList() {
      List<String> result1 = new ArrayList<>();
      for (Integer integer2 : Arrays.asList(1, 2, 3, 4)) {
          String string1 = String.valueOf(integer2);
          result1.add(string1);
      }
      Object list1 = result1;
      List<String> result = new ArrayList<>();
      for (Integer i2 : Arrays.asList(1, 2, 3, 4)) {
          String s1 = String.valueOf(i2);
          result.add(s1);
      }
      Iterable<String> list2 = result;
      Collection<String> list3 = new ArrayList<>();
      for (Integer integer1 : Arrays.asList(1, 2, 3, 4)) {
          String value = String.valueOf(integer1);
          list3.add(value);
      }
      List<String> list4 = new ArrayList<>();
      for (Integer i1 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf = String.valueOf(i1);
          list4.add(valueOf);
      }
      Collection<Object> list5 = new ArrayList<>();
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String string = String.valueOf(integer);
          list5.add(string);
      }
      List<String> list = new ArrayList<>();
      for (Integer i : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(i);
          list.add(s);
      }
      Collection<?> list6 = list;
  }

  public void testPartition() {
      Map<Boolean, List<String>> map = new HashMap<>();
      map.put(false, new ArrayList<>());
      map.put(true, new ArrayList<>());
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(integer);
          map.get(s.length() > 1).add(s);
      }
      Object map1 = map;
      Map<Boolean, List<String>> map2 = new HashMap<>();
      map2.put(false, new ArrayList<>());
      map2.put(true, new ArrayList<>());
      for (Integer i : Arrays.asList(1, 2, 3, 4)) {
          String x = String.valueOf(i);
          map2.get(x.length() > 1).add(x);
      }
  }

  public void testGroupingBy() {
      TreeMap<Integer, Set<String>> result = new TreeMap<>();
      for (Integer i2 : Arrays.asList(1, 2, 3, 4)) {
          String s1 = String.valueOf(i2);
          result.computeIfAbsent(s1.length(), k2 -> new HashSet<>()).add(s1);
      }
      Object map1 = result;
      TreeMap<Integer, Set<String>> map2 = new TreeMap<>();
      for (Integer integer1 : Arrays.asList(1, 2, 3, 4)) {
          String value = String.valueOf(integer1);
          map2.computeIfAbsent(value.length(), key1 -> new HashSet<>()).add(value);
      }
      NavigableMap<Integer, Set<String>> map3 = new TreeMap<>();
      for (Integer i1 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf = String.valueOf(i1);
          map3.computeIfAbsent(valueOf.length(), k1 -> new HashSet<>()).add(valueOf);
      }
      SortedMap<Integer, Set<String>> map4 = new TreeMap<>();
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String string = String.valueOf(integer);
          map4.computeIfAbsent(string.length(), key -> new HashSet<>()).add(string);
      }
      TreeMap<Integer, Set<String>> map = new TreeMap<>();
      for (Integer i : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(i);
          map.computeIfAbsent(s.length(), k -> new HashSet<>()).add(s);
      }
      Cloneable map5 = map;
  }
}
