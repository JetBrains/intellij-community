import java.util.*;
class Test {
  void foo(Map<String, ? extends List<String>> m) {
      List<String> l = m.get("");
      l.get(0);
  }
}