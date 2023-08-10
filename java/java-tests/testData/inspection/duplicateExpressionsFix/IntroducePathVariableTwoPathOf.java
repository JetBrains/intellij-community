import java.io.*;
import java.nio.file.*;

class C {
  public boolean isRelativeDirectory() {
    String fileName = "foo";
    if (Path.of(fileName<caret>).isAbsolute()) return false;
    System.out.println(fileName);
    return Files.isDirectory(Path.of(fileName));
  }
}