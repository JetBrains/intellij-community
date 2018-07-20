// "Replace with 'StandardCharsets.UTF_8'" "true"
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.*;

class Test {
  void test(byte[] bytes) throws Exception {
    int UTF_8 = 1;
    String string = new String(bytes, StandardCharsets.UTF_8);
    System.out.println(string);
    string.getBytes(UTF_16);
  }
}