// "Add '.toString(StandardCharsets.UTF_8)' call" "true-preview"
import java.io.*;

class X {
  void test() {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    System.out.println(<caret>byteArrayOutputStream);
  }
}