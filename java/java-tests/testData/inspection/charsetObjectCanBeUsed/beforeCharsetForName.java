// "Replace with 'StandardCharsets.UTF_8'" "true"
import java.nio.charset.Charset;

class CharsetForName {
  void test() {
    Charset charset = Charset.forName("<caret>UTF8");
  }
}