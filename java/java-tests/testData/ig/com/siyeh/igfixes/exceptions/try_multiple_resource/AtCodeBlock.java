import java.io.*;
class Simple {
  void foo(File file1, File file2) throws IOException {
    try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {
      <caret>System.out.println(in + ", " + out);
    }
  }
}