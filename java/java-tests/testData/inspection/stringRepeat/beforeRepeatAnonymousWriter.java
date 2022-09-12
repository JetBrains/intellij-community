// "Replace with 'String.repeat()'" "false"
import java.io.StringWriter;

class Test {
  String hundredSpaces() {
    StringWriter sw = new StringWriter() {
      public void write(String text) {
        super.write(Math.random());
      }
    };
    f<caret>or(int i=0; i<100; i++) {
      sw.write(" ");
    }
    return sw.getBuffer().toString();
  }
}