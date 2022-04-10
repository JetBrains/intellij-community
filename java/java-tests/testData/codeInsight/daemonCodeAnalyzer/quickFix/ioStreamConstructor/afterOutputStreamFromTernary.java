// "Replace with 'Files.newOutputStream'" "true"
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(String str, boolean b) {
    try (OutputStream os = (b ? (Files.newOutputStream(Paths.get("foo"))) : (Files.newOutputStream(Paths.get(str))))) {
    } catch (IOException e) {}
  }
}