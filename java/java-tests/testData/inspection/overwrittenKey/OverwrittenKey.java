import java.util.*;

class OverwrittenKey {
  void fillMap(Map<String, Integer> map) {
    map.put(<warning descr="Duplicating Map key">"a"</warning>, 1);
    map.put("b", 2);
    map.put(<warning descr="Duplicating Map key">"c"</warning>, 3);
    map.put("d", 4);
    map.put(<warning descr="Duplicating Map key">"a"</warning>, 5);
    map.put(<warning descr="Duplicating Map key">"a"</warning>, 6);
    map.put("e", 7);
    map.put("f", 8);
    map.put(<warning descr="Duplicating Map key">"c"</warning>, 9);
  }

  void fillSet(HashSet<Integer> set, HashSet<Integer> set2) {
    set.add(5234);
    set.add(<warning descr="Duplicating Set element">5235</warning>);
    set.add(3452);
    set.add(3256);
    set.add(<warning descr="Duplicating Set element">4635</warning>);
    set.add(<warning descr="Duplicating Set element">4635</warning>);
    set.add(3252);
    set.add(2352);
    set.add(5253);
    set.add(<warning descr="Duplicating Set element">5235</warning>);
    set.add(2145);
    set2.add(2145);
  }

  enum Test {
    A,B,C,D,E
  }

  Map<Test, String> map = new HashMap<Test, String>() {{
    put(Test.A, "a");
    put(Test.B, "b");
    this.put(<warning descr="Duplicating Map key">Test.C</warning>, "c");
    put(<warning descr="Duplicating Map key">Test.C</warning>, "d");
    put(Test.E, "e");
  }};
}