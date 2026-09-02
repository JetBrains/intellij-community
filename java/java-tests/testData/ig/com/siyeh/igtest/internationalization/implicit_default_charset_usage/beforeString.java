// "Add 'StandardCharsets.UTF_8' argument" "true-preview"
import java.io.*;

class X {
  void test(byte[] bytes) {
    String s = new Stri<caret>ng(bytes)
  }
}