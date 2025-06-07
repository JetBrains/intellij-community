import java.io.IOException;
import java.io.InputStream;

class Test {
  static class A extends RuntimeException {
    A(String message) {
      super(message);
    }
  }

  void method(InputStream stream) {
      try (stream) {

      } catch (IOException | A e) {
      }
  }
}