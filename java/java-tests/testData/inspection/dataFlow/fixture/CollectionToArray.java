import java.util.*;

import org.jetbrains.annotations.Contract;

public class CollectionToArray {

  String[] testEmpty() {
    List<String> list = new ArrayList<>();
    return list.toArray(new String[0]);
  }

  void test(List<Object> obj) {
    if (obj.isEmpty()) return;
    Object[] objects = obj.toArray(new Object[0]);
    if (<warning descr="Condition 'objects.length == 0' is always 'false'">objects.length == 0</warning>) {}
    if (objects.length == 1) {}
    Object[] objects2 = obj.toArray(new Object[obj.size()]);
    if (<warning descr="Condition 'objects2 == null' is always 'false'">objects2 == null</warning>) {}
    if (<warning descr="Condition 'objects2.length == 0' is always 'false'">objects2.length == 0</warning>) {}
    String[] s = (<warning descr="Casting 'objects' to 'String[]' will produce 'ClassCastException' for any non-null value">String[]</warning>) objects;
  }

  void test1(List<?> list) {
    Object[] array = list.toArray(new Object[15]);
    if (<warning descr="Condition 'array.length < 15' is always 'false'">array.length < 15</warning>) {}
    if (array.length < 16) {}
  }

  void testModify(Collection<?> c) {
    Object[] arr = new Object[10];
    arr[0] = null;
    if (<warning descr="Condition 'arr[0] == null' is always 'true'">arr[0] == null</warning>) {}
    int size = c.size();
    Object[] objects = c.toArray(arr);
    if (arr[0] == null) {} // could be overwritten
    if (<warning descr="Condition 'size == c.size()' is always 'true'">size == c.size()</warning>) {}
  }

  void test2(List<?> list) {
    if (list.isEmpty()) return;
    Object[] objects = list.toArray();
    if (<warning descr="Condition 'objects.length == 0' is always 'false'">objects.length == 0</warning>) {}
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  void test2(Set set) {
    Integer[] arr = (<warning descr="Casting 'set.toArray(...)' to 'Integer[]' will produce 'ClassCastException' for any non-null value">Integer[]</warning>) set.toArray(new Number[0]);
  }

  void testExact(Set<?> set) {
    if (set.size() == 10) {
      Object[] arr = set.toArray(new Object[15]);
      if (<warning descr="Condition 'arr.length == 15' is always 'true'">arr.length == 15</warning>) {}
    }
    if (set.size() == 15) {
      Object[] arr = set.toArray(new Object[10]);
      if (<warning descr="Condition 'arr.length == 15' is always 'true'">arr.length == 15</warning>) {}
    }
  }
  
  void testPresizedArray(List<?> list) {
    if (list.size() == 10) {
      Object[] data = list.toArray(new Object[list.size()]);
      if (<warning descr="Condition 'data.length == 10' is always 'true'">data.length == 10</warning>) {}
    }
  }

  void testPresizedArray2(List<?> list) {
    Object[] data = list.toArray(new Object[list.size()]);
    if (<warning descr="Condition 'list.size() == data.length' is always 'true'">list.size() == data.length</warning>) {}
  }

  void testRaw(java.util.List l) {  
    final String[][] ss = (<warning descr="Casting 'l.toArray(...)' to 'String[][]' will produce 'ClassCastException' for any non-null value">String[][]</warning>) l.toArray(new Number[l.size()]);
  }

  @Contract(pure = true)
  String[] testAnyArray(List<String> list, String[] arr) {
    return list.toArray(<warning descr="Immutable object is passed where mutable is expected">arr</warning>);
  }

  @Contract(pure = true)
  String[] testEmptyArray(List<String> list, String[] arr) {
    assert arr.length == 0;
    return list.toArray(arr);
  }
  
  void testSizeEquality(List<String> list, int x) {
    String[] arr = list.toArray(new String[0]);
    if (<warning descr="Condition 'x == 1 && list.get(arr.length).isEmpty()' is always 'false'">x == 1 && list.<warning descr="The call to 'get' always fails as index is out of bounds">get</warning>(arr.length).isEmpty()</warning>) {
    }
    if (<warning descr="Condition 'x == 2 && arr[list.size()].isEmpty()' is always 'false'">x == 2 && arr[<warning descr="Array index is out of bounds">list.size()</warning>].isEmpty()</warning>) {
    }
    if (list.isEmpty()) return;
    if (<warning descr="Condition 'arr.length == 0' is always 'false'">arr.length == 0</warning>) return;
  }
}
