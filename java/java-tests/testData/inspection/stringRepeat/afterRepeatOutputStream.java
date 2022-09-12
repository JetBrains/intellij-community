// "Replace with 'String.repeat()'" "true"
import java.io.ByteArrayOutputStream;

class Test {
  String hundredSpaces() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
      baos.write(String.valueOf(" ".getBytes()).repeat(Math.max(0, 100)));
    return baos.toString();
  }
}