// "Fix all 'Simplify stream API call chains' problems in file" "true"

import java.util.*;

class Test {
  public boolean testAnyMatch(Map<String, Integer> map) {
    map.entrySet().stream().m<caret>ap(Map.Entry::getKey).forEach(System.out::println);
    map.entrySet().stream().map(Map.Entry::getValue).forEach(System.out::println);
    map/*1*/.entrySet()/*2*/.stream()./*3*/map(e -> /*4*/e.getKey())/*5*/.forEach(System.out::println);
    map.entrySet().stream().map(e -> {
      return e.getValue();
    }).forEach(System.out::println);
  }

  class MyMap extends Map<Integer, String> {
    void process() {
      entrySet().stream().map(Map.Entry::getKey).forEach(System.out::println);
    }
  }
}