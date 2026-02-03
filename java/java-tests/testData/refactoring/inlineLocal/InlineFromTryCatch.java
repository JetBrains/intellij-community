import java.io.*;

class Test {
  private static InputStream getInputUnchecked() throws IOException {
    InputStream in;
    try {
      in = ff();
    }
    catch (IOException e) {
      throw new IOException();
    }
    return i<caret>n;
  }

  static InputStream ff() throws IOException {
    return null;
  }
}