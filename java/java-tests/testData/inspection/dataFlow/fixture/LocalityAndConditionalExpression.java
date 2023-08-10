import java.util.*;

class Test {
  void test(boolean b) {
    Set<String> set1 = new HashSet<>();
    Set<String> set2 = new HashSet<>();
    Set<String> activeSet = b ? set1 : set2;
    activeSet.add("foo");
    if (set1.isEmpty()) {
      if (<warning descr="Condition 'b' is always 'false'">b</warning>) {}
    }
  }
}