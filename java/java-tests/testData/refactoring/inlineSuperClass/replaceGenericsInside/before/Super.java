import java.util.*;

class Super<T> {
  List<T> l;

  Super() {
    Set<T> s = new HashSet<T>();
    for (T t : s) {
      System.out.println(t);
    }
  }

  void foo(T t) {
    System.out.println(t);
  }

  void bar() {
    for (T t : l) {
      System.out.println(t);
    }
  }
}