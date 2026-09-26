import java.util.*;

class Foo {
  void test() {
    List<String> list = Arrays.asList("a", "b", null, "c");
    int[] arr2 = {1,2,3};
    Integer[] arr3 = {1,2,3};
    List<?> list2 = Arrays.asList(arr2);
    List<?> list3 = Arrays.asList(arr3);
    unknown();
    if (<warning descr="Condition 'list.size() == 4' is always 'true'">list.size() == 4</warning>) {}
    if (<warning descr="Condition 'list2.size() == 1' is always 'true'">list2.size() == 1</warning>) {}
    if (<warning descr="Condition 'list3.size() == 3' is always 'true'">list3.size() == 3</warning>) {}
  }
  
  void test2(String[] arr) {
    List<String> list = Arrays.asList(arr);
    if (<warning descr="Condition 'list.size() == arr.length' is always 'true'">list.size() == arr.length</warning>) {}
  }

  native void unknown();
}