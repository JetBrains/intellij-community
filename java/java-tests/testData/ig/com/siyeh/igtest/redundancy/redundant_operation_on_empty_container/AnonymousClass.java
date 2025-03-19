import java.util.*;

class Clazz {
  public void foo() {
    List<String> merchantInfos = new ArrayList<String>(1) {{ // not empty
      addAll(Arrays.asList("foo", "bar"));
    }};
    merchantInfos.forEach(System.out::println);
  }
}