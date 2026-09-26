import test.MyBufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

class Multiple {

  void m() throws IOException {
      try (FileInputStream fileInputStream = new FileInputStream(""); MyBufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
      }
  }
}