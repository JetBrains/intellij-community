// "Replace with 'String.repeat()'" "true"
import java.io.ByteArrayOutputStream;

class Test {
  String hundredSpaces() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    f<caret>or(int i=0; i<100; i++) {
      baos.write(" ".getBytes());
    }
    return baos.toString();
  }
}