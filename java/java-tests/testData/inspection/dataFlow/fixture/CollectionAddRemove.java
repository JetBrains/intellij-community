import java.util.*;

public class CollectionAddRemove {
  void testAdd(List<String> list) {
    list.add("foo");
    if (<warning descr="Condition 'list.isEmpty()' is always 'false'">list.isEmpty()</warning>) { }
  }
  void testAdd(Set<String> set) {
    set.add("foo");
    if (<warning descr="Condition 'set.isEmpty()' is always 'false'">set.isEmpty()</warning>) { }
  }
  void testAdd3() {
    List<String> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add("baz");
    if (<warning descr="Condition 'list.size() == 3' is always 'true'">list.size() == 3</warning>) { }
    Set<String> set = new HashSet<>();
    set.add("foo");
    if (<warning descr="Condition 'set.size() == 1' is always 'true'">set.size() == 1</warning>) { }
    set.add("bar");
    set.add("baz");
    if (set.size() == 3) { }
    if (<warning descr="Condition 'set.isEmpty()' is always 'false'">set.isEmpty()</warning>) {}
  }
  void testAddLoop() {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      list.add("foo");
      if (<warning descr="Condition 'list.isEmpty()' is always 'false'">list.isEmpty()</warning>) { }
    }
  }

  void testAddAll(List<String> list1, List<String> list2) {
    list1.addAll(list2);
    if (list1.isEmpty()) return;
    list2.addAll(list1);
    if (<warning descr="Condition 'list2.isEmpty()' is always 'false'">list2.isEmpty()</warning>) return;
    List<String> l1 = new ArrayList<>();
    l1.add("foo");
    l1.add("bar");
    List<String> l2 = new ArrayList<>();
    l2.addAll(l1);
    l2.addAll(l1);
    if (<warning descr="Condition 'l2.size() == 4' is always 'true'">l2.size() == 4</warning>) {}
    l1.addAll(l2);
    l2.addAll(l1);
    l1.addAll(l2);
    l1.addAll(l1);
    if (<warning descr="Condition 'l1.size() == 20' is always 'false'">l1.size() == 20</warning>) {}
    Set<String> s = new HashSet<>();
    s.addAll(l1);
    if (s.size() > 19) {}
    if (s.size() > 20) {}
  }

  void testAddAllArraysAsList(String[] data) {
    Set<String> set = new HashSet<>();
    if (data.length == 0) return;
    set.addAll(Arrays.asList(data));
    if (<warning descr="Condition 'set.isEmpty()' is always 'false'">set.isEmpty()</warning>) {}
  }

  void testRemove() {
    List<String> list = new ArrayList<>();
    list.remove("foo");
    if (<warning descr="Condition 'list.isEmpty()' is always 'true'">list.isEmpty()</warning>) {}
    list.remove("foo");
    if (<warning descr="Condition 'list.isEmpty()' is always 'true'">list.isEmpty()</warning>) {}
    list.add("foo");
    list.add("bar");
    list.add("baz");
    list.remove("foo");
    list.remove("bar");
    list.remove("baz");
    if (<warning descr="Condition 'list.size() > 3' is always 'false'">list.size() > 3</warning>) {}
    list.<warning descr="The call to 'remove' always fails as index is out of bounds">remove</warning>(100);
  }

  void testRemoveByIndex() {
    List<String> list2 = new ArrayList<>();
    list2.add(0, "foo");
    list2.add(0, "bar");
    if (<warning descr="Condition 'list2.size() == 2' is always 'true'">list2.size() == 2</warning>) {}
    list2.remove(1);
    list2.remove(0);
    if (<warning descr="Condition 'list2.isEmpty()' is always 'true'">list2.isEmpty()</warning>) {}
  }

  // IDEA-250778
  public static void main(String[] args) {
    List<String> l = new ArrayList<>();
    for (String arg : args) {
      l.add(arg);
    }
    l.add("some str");
    int bestLen = 100;
    String bestStr = null;
    for (String s : l) {
      if (bestStr == null || s.length() > bestLen) {
        bestStr = s;
        bestLen = s.length();
      }
    }
    System.out.println(bestStr.length());
  }

  void collectionDeclaredType() {
    final Collection<String> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add("baz");

    if (<warning descr="Condition 'list.size() == 2' is always 'false'">list.size() == 2</warning>) { 
    }
  }

  void arrayListCtorKeepsLocality() {
    ArrayList<String> strings = new ArrayList<>();
    strings.add("foo");
    strings.add("bar");
    strings.add("baz");

    someSideEffect(new ArrayList<>(strings)); 

    if (<warning descr="Condition '!strings.isEmpty()' is always 'true'">!<warning descr="Result of 'strings.isEmpty()' is always 'false'">strings.isEmpty()</warning></warning>) { 
      System.out.println("ok");
    }
  }
  
  native void someSideEffect(Object obj);
}