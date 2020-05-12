import java.util.*;

class Test {
  int x = 5;
  int[] arr = new int[10];
  final int[] arr2 = new int[10];

  final int getFoo() {
    return Math.min(arr2.length, 5);
  }

  void test(List<String> list, AbstractList<String> list2) {
    List<String> list3 = new ArrayList<>();
    x = 6;
    arr[1] = 2;
    arr2[1] = 2;
    if (getFoo() != 10) return;
    // local list, cannot alias to Test, so cannot change anything
    list3.add("1");
    if (<warning descr="Condition 'x == 6' is always 'true'">x == 6</warning>) { }
    if (<warning descr="Condition 'arr[1] == 2' is always 'true'">arr[1] == 2</warning>) { }
    if (<warning descr="Condition 'arr2[1] == 2' is always 'true'">arr2[1] == 2</warning>) { }
    if (<warning descr="Condition 'getFoo() != 10' is always 'false'">getFoo() != 10</warning>) return;
    String s = "foobar";
    char[] data = new char[10];
    // changes "data" only; "data" is still local, so getFoo() cannot depend on it
    s.getChars(0, 3, data, 1);
    Arrays.sort(data);
    if (<warning descr="Condition 'x == 6' is always 'true'">x == 6</warning>) { }
    if (<warning descr="Condition 'arr[1] == 2' is always 'true'">arr[1] == 2</warning>) { }
    if (<warning descr="Condition 'arr2[1] == 2' is always 'true'">arr2[1] == 2</warning>) { }
    if (<warning descr="Condition 'getFoo() != 10' is always 'false'">getFoo() != 10</warning>) return;
    // local StringBuilder, cannot change anything but we cannot track locality through the call chain,
    // so we don't know that append() is called on local object and assume that it could be visible for getFoo()
    String s1 = new StringBuilder().append("foo").append("bar").toString(); 
    if (<warning descr="Condition 'x == 6' is always 'true'">x == 6</warning>) { }
    if (<warning descr="Condition 'arr[1] == 2' is always 'true'">arr[1] == 2</warning>) { }
    if (<warning descr="Condition 'arr2[1] == 2' is always 'true'">arr2[1] == 2</warning>) { }
    if (getFoo() != 10) return;
    list2.add("1"); // non-local list, cannot alias to Test, but getFoo() method result can depend on it
    if (<warning descr="Condition 'x == 6' is always 'true'">x == 6</warning>) { }
    if (<warning descr="Condition 'arr[1] == 2' is always 'true'">arr[1] == 2</warning>) { }
    if (<warning descr="Condition 'arr2[1] == 2' is always 'true'">arr2[1] == 2</warning>) { }
    if (getFoo() != 10) return;
    if (list != this) {
      list.add("1"); // non-local list, cannot alias to Test (guarded by relation)
      if (<warning descr="Condition 'x == 6' is always 'true'">x == 6</warning>) { }
      if (<warning descr="Condition 'arr[1] == 2' is always 'true'">arr[1] == 2</warning>) { }
      if (<warning descr="Condition 'arr2[1] == 2' is always 'true'">arr2[1] == 2</warning>) { }
    }
    list.add("1"); // list could alias to Test (SubTest extends Test implements List), so Test fields could change
    if (x == 6) { }
    if (arr[1] == 2) { } // array element cannot be changed but array itself can be
    if (<warning descr="Condition 'arr2[1] == 2' is always 'true'">arr2[1] == 2</warning>) { } // arr2 is final and its element cannot be changed
  }
  
  void testLocalLeak(String[] data) {
    List<String> list = new ArrayList<>();
    Collections.addAll(list, data); // no leak
    Collections.sort(list);
    if (list.isEmpty()) return;
    unknown();
    if (<warning descr="Condition 'list.isEmpty()' is always 'false'">list.isEmpty()</warning>) return;
  }
  
  native void unknown();

  void testNewString(char[] data) {
    String s = new String(data);
    if ("foo".equals(s)) {

    }
  }
}