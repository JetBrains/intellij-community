// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Paths;

class Foo {
  void test(String path, boolean b, boolean b1) {
    Path name = Paths.get(b ? b1 ? "foo" : "bar" : "baz");
    path = "bar";
    try (InputStream is = new FileInputStre<caret>am(b ? b1 ? "foo" : "bar" : "baz")) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}