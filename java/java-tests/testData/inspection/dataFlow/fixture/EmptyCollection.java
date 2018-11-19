import java.util.*;

class EmptyCollection {
  void test() {
    List<String> list = fill(new ArrayList<String>());
    for(String s : list) {
      System.out.println(s);
    }
    list = fill(Collections.<String>emptyList());
    for(String s : <warning descr="Collection 'list' is always empty">list</warning>) {
      System.out.println(s);
    }
  }
  
  static List<String> fill(List<String> list) {
    list.add("foo");
    return list;
  }
}