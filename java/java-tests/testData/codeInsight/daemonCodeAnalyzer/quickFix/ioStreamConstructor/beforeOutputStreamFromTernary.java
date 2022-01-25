// "Replace with 'Files.newOutputStream'" "true"
import java.io.*;

class Foo {
  void test(String str, boolean b) {
    try (OutputStream os = (b ? (Files.newOutputStream(Path.of("foo"))) : (new FileO<caret>utputStream(str)))) {
    } catch (IOException e) {}
  }
}