import java.util.*;
import java.io.*;

public class MutabilityJdk {
  static final List<String> LIST = Collections.unmodifiableList(Arrays.asList("foo", "bar"));

  void testField() {
    LIST.<warning descr="Immutable object is modified">add</warning>("baz");
  }

  void testEmpty() {
    List<String> list = Collections.emptyList();
    Collections.sort(<warning descr="Immutable object is passed where mutable is expected">list</warning>);
  }

  void testUnmodifiable(Collection<String> collection) {
    collection = Collections.unmodifiableCollection(collection);
    collection.<warning descr="Immutable object is modified">add</warning>("foo");
  }

  void testBranch(boolean b) {
    List<Object> list;
    if(b) {
      list = Collections.emptyList();
    } else {
      list = new ArrayList<>();
    }
    if(!b) {
      list.add("foo");
    }
    list.<warning descr="Immutable object is modified">add</warning>("bar");
  }

  void testArrayGtZero(File configRoot) {
    final File[] files = configRoot.listFiles();

    final Map<String, File> templatesOnDisk = files != null && files.length > 0 ? new HashMap<>() : Collections.emptyMap();
    if (files != null) {
      for (File file : files) {
        if (!file.isDirectory()) {
          final String name = file.getName();
          templatesOnDisk.put(name, file);
        }
      }
    }
  }

  // Purity is inferred
  static Runnable testLambda(List<String> list) {
    return () -> list.add("foo");
  }

  interface X {
    List<Object> get();
  }

  List<String> getList(X x) {
    List<String> result = Collections.emptyList();
    for (Object obj : x.get()) {
      if (obj instanceof String) {
        if (result.isEmpty()) result = new ArrayList<>();
        result.add((String)obj);
      }
    }
    return result;
  }

  void testNoFlush() {
    List<String> list1 = Collections.emptyList();
    List<String> list2 = new ArrayList<>();
    List<String> list3 = Collections.unmodifiableList(list2);
    if(<warning descr="Condition 'list1.isEmpty()' is always 'true'">list1.isEmpty()</warning>) System.out.println("ok");
    if(!list3.isEmpty()) return;
    if(<warning descr="Condition '!list3.isEmpty()' is always 'false'">!list3.isEmpty()</warning>) return;
    list2.add("foo");
    // list1 size is not flushed (UNMODIFIABLE)
    if(<warning descr="Condition 'list1.isEmpty()' is always 'true'">list1.isEmpty()</warning>) System.out.println("ok");
    // list3 size is flushed (UNMODIFIABLE_VIEW)
    if(!list3.isEmpty()) return;
  }

  static class IncompleteCode {
    <error descr="Variable 'list' might not have been initialized">final List<String> list</error>;

    IncompleteCode() {
      list = (IncompleteCode)((<error descr="Expression expected">)</error><error descr="')' expected"><error descr="';' expected">b</error></error>ar()<error descr="';' expected"><error descr="Unexpected token">)</error></error><error descr="Unexpected token">.</error><error descr="Cannot resolve method 'baz' in 'IncompleteCode'">baz</error>();
    }

    static native Object bar();

    void test() {
      list.add("foo");
    }
  }
}
