// "Add '.toString(StandardCharsets.UTF_8)' call" "true-preview"
import java.io.*;
import java.nio.charset.StandardCharsets;

class X {
  void test() {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    System.out.println(byteArrayOutputStream.toString(StandardCharsets.UTF_8));
  }
}