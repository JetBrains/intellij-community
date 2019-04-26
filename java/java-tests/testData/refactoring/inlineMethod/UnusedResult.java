import java.util.*;

class Test {
  boolean test(Set<String> set) {
    return set.add("foo");
  }

  void use() {
    Set<String> set = new HashSet<>();
    <caret>test(set);
    test(set);
  }
}