// "Add 'StandardCharsets.UTF_8' argument" "true-preview"
import java.io.*;

class X {
  void test(OutputStream os) {
    Writer writer = new Ou<caret>tputStreamWriter(os);
  }
}