// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(String path, boolean b, boolean b1) {
    Path name = Paths.get(b ? b1 ? "foo" : path : "baz");
    path = "bar";
    try (InputStream is = Files.newInputStream(Paths.get(b ? b1 ? "foo" : path : "baz"))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}