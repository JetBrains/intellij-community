// "Replace with collect" "true-preview"

import java.util.List;

public class Test {
  String foo(String[] lines) {
    final StringBuilder result = new StringBuilder();
    fo<caret>r (int i = 0; i < lines.length; i++) {
      final String line = lines[i];// extracted into a separate var
      if (i > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }
}