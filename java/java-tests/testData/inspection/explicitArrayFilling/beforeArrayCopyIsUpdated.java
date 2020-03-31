// "Replace loop with 'Arrays.fill()' method call" "true"

public class Test {

  public static String[] init(int n) {
    String[] lines = new String[n];
    String[] copy = getCopy(lines);
    copy[0] = "foo";
    for (<caret>int i = 0; i < lines.length; i++) {
      lines[i] = null;
    }
    return lines;
  }

  private static String[] getCopy(String[] original) {
    return original;
  }
}