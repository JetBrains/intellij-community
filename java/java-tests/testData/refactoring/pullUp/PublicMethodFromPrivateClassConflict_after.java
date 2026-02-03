import java.util.*;
class A {
    void foo() {
      HM<String, String> hm = new HM<>();
      for (Map.Entry<String, String> stringStringEntry : hm.entrySet()) {}
    }

    private static class HM<K, V> extends HashMap<K, V>{}
}
class B extends A {
}