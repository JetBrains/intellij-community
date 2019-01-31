// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InExactVariable {
  public void testMap() {
      HashMap<String, String> map = new HashMap<>();
      for (Integer integer2 : Arrays.asList(1, 2, 3, 4)) {
          String of = String.valueOf(integer2);
          map.putIfAbsent(of.trim(), of);
      }
      Object map1 = map;
      HashMap<String, String> map2 = new HashMap<>();
      for (Integer integer1 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf = String.valueOf(integer1);
          map2.putIfAbsent(valueOf.trim(), valueOf);
      }
      Map<String, String> map3 = new HashMap<>();
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(integer);
          map3.putIfAbsent(s.trim(), s);
      }
  }

  public void testList() {
      List<String> result1 = new ArrayList<>();
      for (Integer integer5 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf1 = String.valueOf(integer5);
          result1.add(valueOf1);
      }
      Object list1 = result1;
      List<String> result = new ArrayList<>();
      for (Integer integer4 : Arrays.asList(1, 2, 3, 4)) {
          String s1 = String.valueOf(integer4);
          result.add(s1);
      }
      Iterable<String> list2 = result;
      Collection<String> list3 = new ArrayList<>();
      for (Integer integer3 : Arrays.asList(1, 2, 3, 4)) {
          String value = String.valueOf(integer3);
          list3.add(value);
      }
      List<String> list4 = new ArrayList<>();
      for (Integer integer2 : Arrays.asList(1, 2, 3, 4)) {
          String of = String.valueOf(integer2);
          list4.add(of);
      }
      Collection<Object> list5 = new ArrayList<>();
      for (Integer integer1 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf = String.valueOf(integer1);
          list5.add(valueOf);
      }
      List<String> list = new ArrayList<>();
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(integer);
          list.add(s);
      }
      Collection<?> list6 = list;
  }

  public void testPartition() {
      Map<Boolean, List<String>> map = new HashMap<>();
      map.put(false, new ArrayList<>());
      map.put(true, new ArrayList<>());
      for (Integer integer1 : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(integer1);
          map.get(s.length() > 1).add(s);
      }
      Object map1 = map;
      Map<Boolean, List<String>> map2 = new HashMap<>();
      map2.put(false, new ArrayList<>());
      map2.put(true, new ArrayList<>());
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String x = String.valueOf(integer);
          map2.get(x.length() > 1).add(x);
      }
  }

  public void testGroupingBy() {
      TreeMap<Integer, Set<String>> result = new TreeMap<>();
      for (Integer integer4 : Arrays.asList(1, 2, 3, 4)) {
          String s1 = String.valueOf(integer4);
          result.computeIfAbsent(s1.length(), k2 -> new HashSet<>()).add(s1);
      }
      Object map1 = result;
      TreeMap<Integer, Set<String>> map2 = new TreeMap<>();
      for (Integer integer3 : Arrays.asList(1, 2, 3, 4)) {
          String value = String.valueOf(integer3);
          map2.computeIfAbsent(value.length(), key1 -> new HashSet<>()).add(value);
      }
      NavigableMap<Integer, Set<String>> map3 = new TreeMap<>();
      for (Integer integer2 : Arrays.asList(1, 2, 3, 4)) {
          String of = String.valueOf(integer2);
          map3.computeIfAbsent(of.length(), k1 -> new HashSet<>()).add(of);
      }
      SortedMap<Integer, Set<String>> map4 = new TreeMap<>();
      for (Integer integer1 : Arrays.asList(1, 2, 3, 4)) {
          String valueOf = String.valueOf(integer1);
          map4.computeIfAbsent(valueOf.length(), key -> new HashSet<>()).add(valueOf);
      }
      TreeMap<Integer, Set<String>> map = new TreeMap<>();
      for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
          String s = String.valueOf(integer);
          map.computeIfAbsent(s.length(), k -> new HashSet<>()).add(s);
      }
      Cloneable map5 = map;
  }
}
