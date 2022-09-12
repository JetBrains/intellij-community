// "Replace with 'String.repeat()'" "true"
import java.io.PrintStream;

class Test {
  void hundredSpaces() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    f<caret>or(int i=0; i<100; i++) {
      ps.print(" ");
    }
    ps.close();
  }
}