// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(String str) {
    try {
      if (str.length() < 3) throw new FileNotFoundException("e");
      try (InputStream in = Files.newInputStream(Paths.get(str))) {
      }
      catch (IOException e) {
        System.out.println("Don't know what happened");
      }
    }
    catch (FileNotFoundException e) {
      System.out.println("file not found exception");
    }
  }
}