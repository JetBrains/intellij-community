import java.io.FileNotFoundException;
import java.io.PrintStream;

class MyTestClass {
  void test() throws FileNotFoundException {
    PrintStream <caret>t = null;
    try (t) {
      System.out.println(t);
    }
  }
}
