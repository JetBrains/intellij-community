import java.util.*;

class Foo<T extends List<Integer>> {

  public void foo() {
    Map<T, T> map = new HashMap<>();
    map.containsKey(<warning descr="Suspicious call to 'Map.containsKey()'">new HashMap<>()</warning>);
  }
}