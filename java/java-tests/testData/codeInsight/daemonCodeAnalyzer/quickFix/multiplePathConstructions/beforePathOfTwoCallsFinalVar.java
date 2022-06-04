// "Extract 'java.nio.file.Path' constructions to variable" "true"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory() {
    String fileName = "foo";
    if (Path.of(fileName<caret>).isAbsolute()) return false;
    System.out.println(fileName);
    return Files.isDirectory(Path.of(fileName));
  }
}