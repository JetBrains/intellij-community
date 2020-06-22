import java.util.*;

class Clazz {
  interface MyRef<T> {}

  void test(List<MyRef<String>> list1, List<String> list2) {
    list1.removeAll(<warning descr="'List<MyRef<String>>' may not contain objects of type 'String'">list2</warning>);
  }

  void f(Map<Integer,String> map) {
    Number n = new Integer(3);
    if (map.containsKey(n)) {
      return;
    }
  }

  void foo(List<? extends Number> c) {
    c.contains(<warning descr="'List<capture of ? extends Number>' may not contain objects of type 'String'">""</warning>);
  }

  // IDEA-126935
  void test() {
    List<Object> elementsToRemove = new ArrayList<Object>();
    elementsToRemove.add("foo");

    List<String> storage = new ArrayList<String>(Arrays.asList("foo", "bar"));
    storage.removeAll(elementsToRemove);  // Warning here
  }
}