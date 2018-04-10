import java.util.*;

class Clazz {
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