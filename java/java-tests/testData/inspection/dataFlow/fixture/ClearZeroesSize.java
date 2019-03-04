import java.util.*;

class X {
  void test(Collection<String> c, List<String> l, Map<String, Integer> map) {
    if(map.isEmpty()) {}
    map.clear();
    if(<warning descr="Condition 'map.isEmpty()' is always 'true'">map.isEmpty()</warning>) {}
    if(<warning descr="Condition 'map.keySet().isEmpty()' is always 'true'">map.keySet().isEmpty()</warning>) {}
    if(<warning descr="Condition 'map.values().isEmpty()' is always 'true'">map.values().isEmpty()</warning>) {}
    if(<warning descr="Condition 'map.entrySet().isEmpty()' is always 'true'">map.entrySet().isEmpty()</warning>) {}

    if(c.isEmpty()) {}
    c.clear();
    if(<warning descr="Condition 'c.isEmpty()' is always 'true'">c.isEmpty()</warning>) {}
    if(l.isEmpty()) {}
    l.clear();
    if(<warning descr="Condition 'l.isEmpty()' is always 'true'">l.isEmpty()</warning>) {}
  }
  
}