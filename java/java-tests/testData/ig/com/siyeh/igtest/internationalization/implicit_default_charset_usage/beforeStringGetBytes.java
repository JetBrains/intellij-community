// "Add 'StandardCharsets.UTF_8' argument" "true-preview"
import java.io.*;

class X {
  void test(String s) {
    byte[] bytes = s.getByt<caret>es();
  }
}