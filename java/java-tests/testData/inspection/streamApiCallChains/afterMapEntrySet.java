// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.*;

class Test {
  public boolean testAnyMatch(Map<String, Integer> map) {
    map.keySet().stream().filter(x -> !x.isEmpty()).forEach(System.out::println);
    map.values().stream().forEach(System.out::println);
      /*3*/
      /*4*/
      map/*1*/.keySet()/*2*/.stream()/*5*/.forEach(System.out::println);
    map.values().stream().forEach(System.out::println);
  }

  class MyMap extends Map<Integer, String> {
    void process() {
      keySet().stream().forEach(System.out::println);
    }
  }
}