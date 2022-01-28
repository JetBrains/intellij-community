import java.util.*;

class EmptyCollection {
  void testEmptyListAsSingleton() {
    List<Throwable> exceptions = Collections.emptyList();
    do {
      try {
        System.out.println("foo");
      }
      catch (Throwable e) {
        if (exceptions == Collections.<Throwable>emptyList()) {
          exceptions = new ArrayList<>();
        }
        exceptions.add(e); // no immutable list is modified warning
      }
    }
    while (true);
  }
  
  void testEmptyMapField() {
    Map<String, String> map = Collections.emptyMap();
    if (<warning descr="Condition 'map == Collections.EMPTY_MAP' is always 'true'">map == Collections.EMPTY_MAP</warning>) {}
    if (map.equals(Collections.EMPTY_MAP)) {}
    if (<warning descr="Condition 'map == Collections.EMPTY_SET' is always 'false'">map == Collections.EMPTY_SET</warning>) {}
  }
  
  void test() {
    List<String> list = fill(new ArrayList<String>());
    for(String s : list) {
      System.out.println(s);
    }
    list = fill(Collections.<String>emptyList());
    if (<warning descr="Condition 'list.isEmpty()' is always 'true'">list.isEmpty()</warning>) {
      
    }
  }
  
  static List<String> fill(List<String> list) {
    list.add("foo");
    return list;
  }

  void custom() {
    SomeDict dictionary = new SomeDict();
    if (dictionary.contains("aaa")) {}
  }
}
class SomeDict extends ArrayList<String> {

  public SomeDict() {
    add("aaa");
    add("bbb");
  }
}