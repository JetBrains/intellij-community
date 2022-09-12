// "Replace with 'String.repeat()'" "false"
import java.io.StringWriter;

class Test {
  String hundredSpaces() {
    StringWriter sw = new StringWriter();
    f<caret>or(int i=0; i<100; i++) {
      sw.write(1);
    }
    return sw.getBuffer().toString();
  }
}