import java.io.FileNotFoundException;
import java.io.PrintStream;

class MyTestClass {
  void test() throws FileNotFoundException {
      try (PrintStream t = null) {
      System.out.println(t);
    }
  }
}
