// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

public class Test {

  public static String[] init(int n) {
    String[] lines = new String[n];
    String[] copy = getCopy(lines);
    copy[0] = "foo";
      Arrays.fill(lines, null);
    return lines;
  }

  private static String[] getCopy(String[] original) {
    return original;
  }
}