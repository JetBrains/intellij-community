// "Replace with 'Files.newOutputStream'" "true"
import java.io.*;
import java.nio.*;

class Foo {
  void test(String str, boolean b) {
    try (OutputStream os = (b ? (Files.newOutputStream(Paths.get("foo"))) : (new FileO<caret>utputStream(str)))) {
    } catch (IOException e) {}
  }
}