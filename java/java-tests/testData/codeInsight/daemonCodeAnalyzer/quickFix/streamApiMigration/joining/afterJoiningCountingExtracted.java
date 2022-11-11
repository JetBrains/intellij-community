// "Replace with collect" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Test {
  String foo(String[] lines) {
    final String result = Arrays.stream(lines).collect(Collectors.joining("\n"));
      // extracted into a separate var
      return result;
  }
}