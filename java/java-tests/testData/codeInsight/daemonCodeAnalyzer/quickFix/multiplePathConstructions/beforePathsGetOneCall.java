// "Extract 'java.nio.file.Path' constructions to variable" "false"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isDirectory(String fileName) {
    return Files.isDirectory(Paths.get(fileNa<caret>me));
  }
}