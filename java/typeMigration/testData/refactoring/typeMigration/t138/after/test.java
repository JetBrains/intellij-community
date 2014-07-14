import java.util.*;
class Test {
  Set<String> mySet = new HashSet();
  void foo(Collection<String> set) {
    mySet.retainAll(set);
  }
}