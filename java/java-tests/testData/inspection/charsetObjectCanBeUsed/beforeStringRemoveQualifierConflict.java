// "Replace with 'StandardCharsets.UTF_8'" "true"
import static java.nio.charset.StandardCharsets.*;

class Test {
  void test(byte[] bytes) throws Exception {
    int UTF_8 = 1;
    String string = new String(bytes, "UTF<caret>-8");
    System.out.println(string);
    string.getBytes(UTF_16);
  }
}