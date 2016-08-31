import java.io.*;

class T {
  private static String getString() throws IOException {
    String s;
    try (BufferedReader r = open()) {
      s = r.readLine();
    }
    <warning descr="Return separated from computation of value of 's'">return s;</warning>
  }

  private static BufferedReader open() throws FileNotFoundException {
    return null;
  }
}