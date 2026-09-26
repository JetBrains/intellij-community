// "Replace with 'StandardCharsets.UTF_8'" "true"
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.stream.Stream;

public class Demo {
  private static void test(InputStream is) {
    try (Stream<String> stream = new BufferedReader(new InputStreamReader(is, "<caret>UTF-8")).lines()) {
      stream.forEach(System.out::println);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
