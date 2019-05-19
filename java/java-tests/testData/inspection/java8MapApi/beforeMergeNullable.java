// "Replace with 'merge' method call" "false"
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class Test {
  static Map<String,String> m = new HashMap<>();

  public static void main(String[] args) {
    addAttribute("some", null);
    System.out.println(m);
  }

  public static void addAttribute(String attributeName, @Nullable String value) {
    final String current = m.get(attributeName);
    if (current <caret>== null) {
      m.put(attributeName, value);
    }
    else {
      m.put(attributeName, current + "," + value);
    }
  }
}