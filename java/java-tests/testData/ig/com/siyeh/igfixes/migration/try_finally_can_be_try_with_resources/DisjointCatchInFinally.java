import java.io.IOException;
import java.io.InputStream;

class Test {
  static class A extends RuntimeException {
    A(String message) {
      super(message);
    }
  }

  void method(InputStream stream) {
    try<caret> {

    } finally {
      try {
        stream.close();
      } catch (IOException | A e) {
      }
    }
  }
}