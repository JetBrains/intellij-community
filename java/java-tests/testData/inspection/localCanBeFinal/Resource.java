import java.io.BufferedReader;
import java.io.FileReader;

class Test {
  String m() {
    try (final BufferedReader br = new BufferedReader(new FileReader("foo"))) {
      return br.readLine();
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}