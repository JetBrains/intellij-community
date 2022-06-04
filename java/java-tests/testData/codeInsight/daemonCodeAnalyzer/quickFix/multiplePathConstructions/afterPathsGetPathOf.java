// "Extract 'java.nio.file.Path' constructions to variable" "true"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(String fileName) {
      Path of = Path.of(fileName);
      if (of.isAbsolute()) return false;
    return Files.isDirectory(of);
  }
}