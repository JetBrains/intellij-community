import java.util.Map;

abstract class B {
  void m(Map<String, String> m) {
    for (String s : m.key<caret>Set()) {
      if (s.startsWith("abc")) {
        String s1 = m.get(s);
        System.out.println(s1);
      }
    }
  }
}
