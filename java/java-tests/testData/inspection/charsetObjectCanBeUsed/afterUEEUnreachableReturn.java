// "Replace with 'StandardCharsets.UTF_8'" "true"
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class Demo {
  public static String someCode(String args) {
      return new String(args.getBytes(StandardCharsets.UTF_8));
      // 1
      // 3
  }
}