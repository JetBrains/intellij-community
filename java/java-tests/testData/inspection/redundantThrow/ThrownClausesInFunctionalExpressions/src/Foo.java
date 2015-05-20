import java.io.*;

class ExceptionTest {

  MyFunction method() {
    return () -> {
      throw new EOFException();
    };
  }

  MyFunction method1() {
    return this::e;
  }

  private void e() throws FileNotFoundException {
    throw new FileNotFoundException();
  }

  @FunctionalInterface
  private interface MyFunction {
    void call() throws FileNotFoundException, EOFException, ObjectStreamException;
  }
}