import java.util.HashMap;

public class LocalVariable {

  void m() {
    final HashMap<String, String> map = new HashMap<caret>() {{
      // comment
      put("a", "b");
      put("a", "b");
      put("a", "b");
      put("a", "b");
    }};
  }
}