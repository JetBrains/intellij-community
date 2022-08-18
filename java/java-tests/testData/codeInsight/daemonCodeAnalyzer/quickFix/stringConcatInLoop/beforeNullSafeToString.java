// "Introduce new StringBuilder to update variable 'res' (null-safe)" "true-preview"
public class SB {
  public static void main(String[] args) {
    String res = null;
    for (String arg : args) {
      if (res == null) {
        res = "[";
      } else {
        res +<caret>= ",";
      }
      res += arg;
    }
    res += "]";
    System.out.println(res);
  }
}

