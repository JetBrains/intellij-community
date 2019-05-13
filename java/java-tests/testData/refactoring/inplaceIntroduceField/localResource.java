import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

class JavaClass {

  static String readFirstLineFromFile() throws IOException {
    try (BufferedReader br = createReader("any")) {
      return b<caret>r.readLine();
    }
  }

  private static BufferedReader createReader(String path) {
    try {
      return new BufferedReader(new FileReader(path));
    } catch (FileNotFoundException e) {
      // for example purposes #createReader shouldn't throw any checked exception
      throw new RuntimeException(e);
    }
  }
}
