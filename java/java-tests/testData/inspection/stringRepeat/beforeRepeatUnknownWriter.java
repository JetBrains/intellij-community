// "Replace with 'String.repeat()'" "false"
import java.io.StringWriter;

class Test {
  private static class AnonymousWriter extends StringWriter() {
    public void write(String text) {
      super.write(Math.random());
    }
  }

  String hundredSpaces() {
    AnonymousWriter sw = new AnonymousWriter();
    f<caret>or(int i=0; i<100; i++) {
      sw.write(" ");
    }
    return sw.getBuffer().toString();
  }
}