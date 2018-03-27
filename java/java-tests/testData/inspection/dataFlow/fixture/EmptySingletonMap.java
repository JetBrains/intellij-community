import java.util.*;
import org.jetbrains.annotations.*;

public class EmptySingletonMap {
  void testEmpty() {
    List<Object> objects = Collections.emptyList();
    for (Object o : <warning descr="Collection 'objects' is always empty">objects</warning>) {
      System.out.println("hello");
    }
  }

  void testMixed(int x) {
    Collection<String> strings;
    if(x > 10) {
      strings = Collections.singleton("foo");
    } else if(x > 6) {
      strings = Collections.emptyList();
    } else if(x > 2) {
      strings = Collections.singletonList("bar");
    } else {
      strings = Arrays.asList("a", "b", "c");
    }
    if(<warning descr="Condition 'strings.size() >= 2 && x > 3' is always 'false'">strings.size() >= 2 && <warning descr="Condition 'x > 3' is always 'false' when reached">x > 3</warning></warning>) {
      System.out.println("never");
    }
  }

  void testMap(boolean b) {
    Map<String, String> map;
    if (b) {
      map = Collections.emptyMap();
    }
    else {
      map = Collections.singletonMap("a", "b");
    }
    if(b && <warning descr="Condition 'map.isEmpty()' is always 'true' when reached">map.isEmpty()</warning>) {
      System.out.println("true");
    }
    if(!b && <warning descr="Condition 'map.size() == 1' is always 'true' when reached">map.size() == 1</warning>) {
      System.out.println("??");
    }
  }

  @Contract(pure = true)
  static Map<String, String> newMap() {
    return new HashMap<>();
  }

  void testDoubleEmpty() {
    Map<String, String> m1 = newMap();
    Map<String, String> m2 = newMap();
    fill(m1, m2);
    if(m1.isEmpty() && m2.isEmpty()) {
      System.out.println("both empty");
    }
  }

  void testNonEqual() {
    if(EmptySingletonMap.newMap() == EmptySingletonMap.newMap()) {
      System.out.println("who knows");
    }
  }

  native void fill(Object m1, Object m2);
}
