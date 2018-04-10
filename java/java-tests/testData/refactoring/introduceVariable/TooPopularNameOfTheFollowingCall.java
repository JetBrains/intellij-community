import java.util.*;
class Test {
  void foo(Map<String, ? extends List<String>> m) {
    <selection>m.get("")</selection>.get(0);
  }
}