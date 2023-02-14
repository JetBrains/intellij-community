// "Fix all ''InputStream' and 'OutputStream' can be constructed using 'Files' methods' problems in file" "true"
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(File file, String str) {
    try (InputStream is = Files.newInputStream(file.toPath())) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (InputStream is = Files.newInputStream(file.toPath())) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (InputStream is = Files.newInputStream(Paths.get(str))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (InputStream is = Files.newInputStream(Paths.get(str))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}