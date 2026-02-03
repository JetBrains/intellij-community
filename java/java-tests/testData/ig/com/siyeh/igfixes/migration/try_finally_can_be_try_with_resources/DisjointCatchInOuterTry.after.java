import java.io.IOException;
import java.io.InputStream;

class Test {
  static class A extends RuntimeException {
    A(String message) {
      super(message);
    }
  }

  static class B extends RuntimeException {
    B(String message) {
      super(message);
    }
  }

  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  void method() {
      try (MyAutoCloseable closeable = create()) {
          System.out.println(1);
      } catch (A | B e) {
      }
  }
}