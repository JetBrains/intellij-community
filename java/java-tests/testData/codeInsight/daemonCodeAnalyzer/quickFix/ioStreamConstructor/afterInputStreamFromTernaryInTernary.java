// "Replace with 'Files.newInputStream()'" "true-preview"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(String path, boolean b, boolean b1) {
    Path name = Paths.get(b ? b1 ? "foo" : "bar" : "baz");
    path = "bar";
    try (InputStream is = Files.newInputStream(name)) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}