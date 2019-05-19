// "Replace with 'StandardCharsets.UTF_8'" "true"
import static java.nio.charset.StandardCharsets.*;

class Test {
  void test(byte[] bytes) throws Exception {
    String string = new String(bytes, UTF_8);
    System.out.println(string);
    string.getBytes(UTF_8);
  }
}