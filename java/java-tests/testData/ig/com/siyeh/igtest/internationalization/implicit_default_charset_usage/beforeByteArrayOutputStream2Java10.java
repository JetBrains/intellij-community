// "Add '.toString(StandardCharsets.UTF_8)' call" "false"
import java.io.*;

class X {
  void test(ByteArrayOutputStream byteArrayOutputStream) {
    // adding .toString(StandardCharsets.UTF_8) may throw NPE
    System.out.println(<caret>byteArrayOutputStream);
  }
}