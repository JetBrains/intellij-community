import java.util.*;
import java.io.Serializable;

class Clazz {
  void f(Map<Integer, String> map, Object o) {
    if (o instanceof Integer && map.containsKey(o)) {
      System.out.println();
    }
  }

  void f(Object o, List<Integer> list) {
    if(o instanceof String) {
      o = 123.4;
    }
    if(o instanceof Integer && !list.contains(o)) {
      System.out.println("oops");
    }
    if(list.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>)) {
      System.out.println("suspicious");
    }
  }

  void intersect(Object o, List<Serializable> l1, List<Cloneable> l2, List<CharSequence> l3) {
    if(o instanceof Cloneable) {
      if(o instanceof Serializable) {
        if(o instanceof CharSequence) {
          System.out.println(l1.contains(o));
          System.out.println(l2.contains(o));
          System.out.println(l3.contains(o));
        }
        System.out.println(l1.contains(o));
        System.out.println(l2.contains(o));
        System.out.println(l3.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>));
      }
      System.out.println(l1.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>));
      System.out.println(l2.contains(o));
      System.out.println(l3.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>));
    }
    System.out.println(l1.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>));
    System.out.println(l2.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>));
    System.out.println(l3.contains(<warning descr="Suspicious call to 'List.contains'">o</warning>));
  }
}