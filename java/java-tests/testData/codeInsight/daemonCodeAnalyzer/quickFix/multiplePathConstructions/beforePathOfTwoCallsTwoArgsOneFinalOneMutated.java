// "Extract 'java.nio.file.Path' constructions to variable" "false"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(boolean b, String bar) {
    String fileName = "foo";
    if (Path.of(fileName<caret>, b ? bar + "baz" : "baz").isAbsolute()) return false;
    System.out.println(fileName);
    bar = "bar";
    return Files.isDirectory(Path.of(fileName, b ? bar + "baz" : "baz"));
  }
}