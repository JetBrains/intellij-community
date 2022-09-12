// "Replace with 'String.repeat()'" "true"
import java.io.PrintStream;

class Test {
  void hundredSpaces() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
      ps.print(" ".repeat(100));
    ps.close();
  }
}