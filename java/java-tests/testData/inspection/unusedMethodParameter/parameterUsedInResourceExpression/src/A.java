import java.io.Closeable;
import java.io.IOException;

public class CloseableParam {
  void make(Closeable closeable) throws IOException {
    try (closeable) {
      System.out.println("inside");
    }
  }
}

class Some {
  public static void main(String[] args) throws IOException {
    CloseableParam p = new CloseableParam();
    p.make(() -> {});
  }
}