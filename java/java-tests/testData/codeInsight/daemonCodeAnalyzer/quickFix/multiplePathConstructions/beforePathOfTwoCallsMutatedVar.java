// "Extract 'java.nio.file.Path' constructions to variable" "false"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(boolean b) {
    String fileName = "foo";
    if (Path.of(fileName<caret>).isAbsolute()) return false;
    if (b) fileName = "baz";
    return Files.isDirectory(Path.of(fileName));
  }
}