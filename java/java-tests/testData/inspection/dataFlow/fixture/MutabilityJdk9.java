import java.util.*;

public class MutabilityJdk9 {

  void testList() {
    List<Integer> list = List.of(1,2,3);
    if(<warning descr="Condition 'list.size() > 5' is always 'false'">list.size() > 5</warning>) {
      System.out.println("impossible");
    }
    list.<warning descr="Immutable object is modified">sort</warning>(null);
  }

  void testSet() {
    Set<String> set = Set.of("foo", "bar", "baz", "qux");
    if(<warning descr="Condition 'set.size() == 4' is always 'true'">set.size() == 4</warning>) {
      System.out.println("always");
    }
    set.<warning descr="Immutable object is modified">add</warning>("oops");
  }

  void testMap() {
    Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5);
    if(<warning descr="Condition 'map.size() != 5' is always 'false'">map.size() != 5</warning>) {
      System.out.println("never");
    }
    map.<warning descr="Immutable object is modified">put</warning>("foo", 6);
  }

  void testMapOfEntries() {
    Map<String, Integer> map = Map.ofEntries(Map.entry("x", 1), Map.entry("y", 2));
    if(<warning descr="Condition 'map.size() == 2' is always 'true'">map.size() == 2</warning>) {
      System.out.println("you bet!");
    }
    map.<warning descr="Immutable object is modified">remove</warning>("x");
  }

  // IDEA-167940
  void testImmutable(String a, String b){
    List.of(a).<warning descr="Immutable object is modified">add</warning>(b); //java 9 collection literals do not accept modification

    Collections.singletonList(a).<warning descr="Immutable object is modified">add</warning>(b); //singleton should not be modified
  }

  void testVarArg(String[] str) {
    List<String> list = List.of(str);
    if(str.length > 2) {
      list.<warning descr="Immutable object is modified">add</warning>("foo");
    }
  }
}
