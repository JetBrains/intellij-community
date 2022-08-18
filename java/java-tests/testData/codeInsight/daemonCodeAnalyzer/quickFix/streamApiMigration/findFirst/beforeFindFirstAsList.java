// "Replace with findFirst()" "true-preview"

import java.util.Arrays;

public class Main {
  public static String find() {
    for(String s : Arrays.//comment
      <caret>asList("foo", "bar", "baz")) {
      if (s.contains("z")) return s;
    }
    return "";
  }
}