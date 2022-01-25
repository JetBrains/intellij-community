// "Replace with 'Files.newOutputStream'" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

class Foo {
  void test(String str, boolean b) {
    try (OutputStream os = (b ? (Files.newOutputStream(Path.of("foo"))) : (Files.newOutputStream(Path.of(str))))) {
    } catch (IOException e) {}
  }
}