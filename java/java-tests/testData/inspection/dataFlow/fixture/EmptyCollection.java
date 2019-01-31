import java.util.*;

class EmptyCollection {
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
}