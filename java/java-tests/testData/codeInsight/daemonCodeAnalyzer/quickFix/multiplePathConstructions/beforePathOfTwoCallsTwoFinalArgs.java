// "Extract 'java.nio.file.Path' constructions to variable" "true"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(String bar) {
    String fileName = "foo";
    if (Path.of(fileName<caret>, bar + "baz").isAbsolute()) return false;
    System.out.println(fileName);
    return Files.isDirectory(Path.of(fileName, bar + "baz"));
  }
}