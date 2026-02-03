// "Replace with 'StandardCharsets.UTF_8'" "true"
import java.io.UnsupportedEncodingException;

public class Demo {
  public static String someCode(String args) {
    try {
      return new String(args.getBytes(<caret>"UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    // 1
    System.out.println("unreachable");
    // 2
    return "";
    // 3
  }
}