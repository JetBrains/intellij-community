// "Replace with 'String.repeat()'" "true"
import java.io.StringWriter;

class Test {
  String hundredSpaces() {
    StringWriter sw = new StringWriter();
      sw.write(" ".repeat(100));
    return sw.getBuffer().toString();
  }
}