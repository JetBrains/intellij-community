import java.util.*;
class A {}
class B extends A {
  void fo<caret>o() {
    HM<String, String> hm = new HM<>();
    for (Map.Entry<String, String> stringStringEntry : hm.entrySet()) {}
  }
  private static class HM<K, V> extends HashMap<K, V>{}
}