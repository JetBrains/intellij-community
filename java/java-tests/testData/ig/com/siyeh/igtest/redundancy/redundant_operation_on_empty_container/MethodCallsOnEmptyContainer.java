import java.util.*;

class Calls {
  void testArray() {
    String[] data = new String[0];
    Arrays.sort(<warning descr="Array 'data' is always empty">data</warning>);
    Arrays.fill(<warning descr="Array 'data' is always empty">data</warning>, "foo");
    Arrays.stream(<warning descr="Array 'data' is always empty">data</warning>).forEach(System.out::println);
  }
  
  void testCollection() {
    List<String> list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.clear();
    
    list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.remove("foo");

    list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.replaceAll(String::trim);

    list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.forEach(System.out::println);

    list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.iterator();

    list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.spliterator();

    list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.sort(null);
  }
  
  void testMap() {
    Map<String, String> map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.get("foo");

    map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.remove("foo");

    map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.remove("foo", "bar");

    map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.replace("foo", "bar");

    map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.replace("foo", "bar", "baz");

    map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.forEach((k, v) -> { });
  }
  
  void testClear(List<String> list) {
    list.clear();
    <warning descr="Collection 'list' is always empty">list</warning>.clear();
  }
  
  
}