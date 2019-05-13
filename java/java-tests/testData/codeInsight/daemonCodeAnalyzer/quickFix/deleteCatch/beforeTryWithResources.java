// "Delete catch for 'java.lang.ClassNotFoundException'" "true"
import java.io.*;

class Cl implements AutoCloseable {
  public void close() throws IOException {
    in.close();
  }
}

class a {
    void f() throws IOException {
      try (Cl c = new Cl()) {
        c.close();
      } catch (<caret>ClassNotFoundException e) {
      }
    }
}
