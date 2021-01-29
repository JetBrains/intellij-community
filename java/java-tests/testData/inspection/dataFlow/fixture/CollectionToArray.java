import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CollectionToArray {
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

  void testRaw(java.util.List l) {  
    final String[][] ss = (<warning descr="Casting 'l.toArray(...)' to 'String[][]' will produce 'ClassCastException' for any non-null value">String[][]</warning>) l.toArray(new Number[l.size()]);
  }
}
