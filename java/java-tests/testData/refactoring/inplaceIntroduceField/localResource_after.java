import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

class JavaClass {

    private static BufferedReader br;

    static String readFirstLineFromFile() throws IOException {
    try (br = createReader("any")) {
      return br.readLine();
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
