// "Extract 'java.nio.file.Path' constructions to variable" "true"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(String fileName) {
    if (Path.of(fileName<caret>).isAbsolute()) return false;
    return Files.isDirectory(Paths.get(fileName));
  }
}