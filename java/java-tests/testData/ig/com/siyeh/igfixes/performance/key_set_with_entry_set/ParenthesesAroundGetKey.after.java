import java.util.Map;

abstract class B {
  void m(Map<String, String> m) {
    for (Map.Entry<String, String> entry : m.entry<caret>Set()) {
      if (entry.getKey().startsWith("abc")) {
        String s1 = entry.getValue();
        System.out.println(s1);
      }
    }
  }
}
