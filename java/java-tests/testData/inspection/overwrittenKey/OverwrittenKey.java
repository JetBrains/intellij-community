import java.util.*;
import com.google.common.collect.ImmutableSet;

class OverwrittenKey {
  void fillMap(Map<String, Integer> map) {
    map.put(<warning descr="Duplicate Map key">"a"</warning>, 1);
    map.put("b", 2);
    map.put(<warning descr="Duplicate Map key">"c"</warning>, 3);
    map.put("d", 4);
    map.put(<warning descr="Duplicate Map key">"a"</warning>, 5);
    map.put(<warning descr="Duplicate Map key">"a"</warning>, 6);
    map.put("e", 7);
    map.put("f", 8);
    map.put(<warning descr="Duplicate Map key">"c"</warning>, 9);
  }

  void fillSet(HashSet<Integer> set, HashSet<Integer> set2) {
    set.add(5234);
    set.add(<warning descr="Duplicate Set element">5235</warning>);
    set.add(3452);
    set.add(3256);
    set.add(<warning descr="Duplicate Set element">4635</warning>);
    set.add(<warning descr="Duplicate Set element">4635</warning>);
    set.add(3252);
    set.add(2352);
    set.add(5253);
    set.add(<warning descr="Duplicate Set element">5235</warning>);
    set.add(2145);
    set2.add(2145);
    set2.add(<warning descr="Duplicate Set element">2</warning>);
    set2.add(345);
    set2.add(<warning descr="Duplicate Set element">2</warning>);
  }

  enum Test {
    A,B,C,D,E
  }

  Map<Test, String> map = new HashMap<Test, String>() {{
    put(Test.A, "a");
    put(Test.B, "b");
    this.put(<warning descr="Duplicate Map key">Test.C</warning>, "c");
    put(<warning descr="Duplicate Map key">Test.C</warning>, "d");
    put(Test.E, "e");
  }};

  void java9() {
    Set<String> set = Set.of(<warning descr="Duplicate Set element">"a"</warning>, "b", "c", <warning descr="Duplicate Set element">"a"</warning>);
    Map<String, String> map = Map.of("a", "a", <warning descr="Duplicate Map key">"b"</warning>, "b", "c", "b", <warning descr="Duplicate Map key">"b"</warning>, "d");
    Map<String, String> map2 = Map.ofEntries(Map.entry("a", "a"), Map.entry(<warning descr="Duplicate Map key">"b"</warning>, "b"),
                                             Map.entry("c", "b"), Map.entry(<warning descr="Duplicate Map key">"b"</warning>, "d"));
  }

  enum X {
    A, B, C
  }

  void enumSetOf() {
    EnumSet<X> xx = EnumSet.of(<warning descr="Duplicate Set element">X.A</warning>, X.B, <warning descr="Duplicate Set element">X.A</warning>, X.C);
    System.out.println(xx);
  }

  void guavaImmutableSetOf() {
    ImmutableSet<Double> s = ImmutableSet.of(<warning descr="Duplicate Set element">-10.</warning>, 1d, <warning descr="Duplicate Set element">-10.0</warning>);
    System.out.println(s);
  }

  void testParameter(Map<Object, Object> map, String key) {
    map.put(<warning descr="Duplicate Map key">key</warning>, "k1");
    map.put(<warning descr="Duplicate Map key">key</warning>, "k2");
  }

  void localClass() {
    class X extends HashMap<String, String> {
      class Y {
        void test() {
          put(<warning descr="Duplicate Map key">"a"</warning>, "b");
          put(<warning descr="Duplicate Map key">"a"</warning>, "c");
        }
      }
    }
  }

  void testFallthrough(Map<String, String> map, int id) {
    switch(id) {
      case 1:
        map.put(<warning descr="Duplicate Map key">"foo"</warning>, "bar");
      case 2:
        map.put(<warning descr="Duplicate Map key">"foo"</warning>, "baz");
      default:
        map.put(<warning descr="Duplicate Map key">"foo"</warning>, "qux");
    }
  }

  void testNoFallthrough(Map<String, String> map, int id) {
    switch(id) {
      case 1:
        map.put("foo", "bar");
        break;
      case 2:
        map.put("foo", "baz");
        break;
      default:
        map.put("foo", "qux");
        break;
    }
  }

  void differentQualifiers(Set<Integer> set) {
    set.add(h1.KEY);
    set.add(h2.KEY);
  }

  KeyHolder h1 = new KeyHolder(1);
  KeyHolder h2 = new KeyHolder(2);

  static class KeyHolder {
    final int KEY;

    KeyHolder(int k) {KEY = k;}
  }
}