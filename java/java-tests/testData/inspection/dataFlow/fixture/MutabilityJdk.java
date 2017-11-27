import java.util.*;
import java.io.*;

public class MutabilityJdk {

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
}
