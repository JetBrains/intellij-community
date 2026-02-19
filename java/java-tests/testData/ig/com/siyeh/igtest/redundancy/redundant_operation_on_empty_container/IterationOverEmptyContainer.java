import java.util.*;

class InlineSimpleMethods {
  String[] data;

  void testArray() {
    for(String s : getData()) {}
    if(data.length != 0) return;
    for(String s : <warning descr="Array 'getData()' is always empty">getData()</warning>) {}
  }

  void testAndChainInlining() {
    if (!hasData() && data != null) {
      for (String s : <warning descr="Array 'data' is always empty">data</warning>) { }
    }
  }

  private boolean hasData() {
    return data != null && data.length > 0;
  }

  private String[] getData() {
    return data;
  }
}
class ForEachOverEmpty {
  void testArray(int[][] arr) {
    if(arr.length != 0) return;
    for (int[] ints : <warning descr="Array 'arr' is always empty">arr</warning>) {
      System.out.println(ints.length);
    }
  }

  void testEmptyArray() {
    int[] arr = new int[0];
    for (int i : <warning descr="Array 'arr' is always empty">arr</warning>) {
      System.out.println("never");
    }
    int length = 0;
    int[] arr2 = new int[length];
    for (int i : <warning descr="Array 'arr2' is always empty">arr2</warning>) {
      System.out.println(i);
    }
  }

  void testCollection(Collection<?> c) {
    if(!c.isEmpty()) return;
    for (Object o : <warning descr="Collection 'c' is always empty">c</warning>) {
      System.out.println(o);
    }
  }

  void testParens(Collection<?> c) {
    if(!c.isEmpty()) return;
    for (Object o : (<warning descr="Collection 'c' is always empty">c</warning>)) {
      System.out.println(o);
    }
  }

  void testDirect() {
    for (Object o : <warning descr="Collection 'Collections.emptyList()' is always empty">Collections.emptyList()</warning>) {
      System.out.println(o);
    }
  }

  void testEmpty() {
    List<Object> objects = Collections.emptyList();
    for (Object o : <warning descr="Collection 'objects' is always empty">objects</warning>) {
      System.out.println("hello");
    }
  }
}
class MapSubCollections {
  void testMap(Map<String, String> map) {
    if(!map.isEmpty()) return;
    for(String s : <warning descr="Collection 'map.keySet()' is always empty">map.keySet()</warning>) {}
    for(String s : <warning descr="Collection 'map.values()' is always empty">map.values()</warning>) {}
    for(Map.Entry s : <warning descr="Collection 'map.entrySet()' is always empty">map.entrySet()</warning>) {}
  }
}
