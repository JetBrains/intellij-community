// "Replace with 'String.repeat()'" "true"
import java.io.StringWriter;

class Test {
  String hundredSpaces() {
    StringWriter sw = new StringWriter();
    f<caret>or(int i=0; i<100; i++) {
      sw.write(" ");
    }
    return sw.getBuffer().toString();
  }
}