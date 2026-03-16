package com.siyeh.igtest.performance;

import java.util.*;

public class StaticCollectionInspection {
  // with warning
  private static final Map s_map1 = new HashMap(10);
  private static final List s_list2 = new ArrayList(10);
  private static final Set s_set3 = new HashSet<>();
  private static /* final */ Map s_map4 = Map.of("key", "value");
  private static /* final */ List s_list5 = List.of("a", "b");

  private static final Map s_map6;
  static {
    s_map6 = new HashMap<>();
  }

  // without warning
  private static final Map<String, String> s_map7 = Map.of("a", "A", "b", "B", "c", "C");
  private static final List<String> s_list8 = List.of("a", "b", "c");
  private static final Set<Integer> s_set9 = Set.of(1, 2, 3);
  private static final Map<String, String> s_map10 = Map.copyOf(Collections.emptyMap());
  private static final List<String> s_list11 = List.copyOf(Collections.emptyList());
  private static final Set<String> s_set12 = Set.copyOf(Collections.emptySet());
  private static final List<String> s_list13 = Collections.emptyList();
  private static final Set<String> s_set14 = Collections.emptySet();
  private static final Map s_map15 = Collections.emptyMap();
  private static final List<String> s_list16 = Collections.singletonList("only");
  private static final Set<String> s_set17 = Collections.singleton("only");
  private static final Map<String, String> s_map18 = Collections.singletonMap("key", "value");
  private static final List<String> s_list19 = Arrays.asList("a", "b", "c");
  private static final Map<String, String> s_map20 = Map.ofEntries(
    Map.entry("key1", "value1"),
    Map.entry("key2", "value2")
  );

  private StaticCollectionInspection() {
  }
}