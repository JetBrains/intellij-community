import java.io.*;
import java.nio.file.*;

class C {
  public boolean isRelativeDirectory() {
    String fileName = "foo";
      Path path = Path.of(fileName);
      if (path.isAbsolute()) return false;
    System.out.println(fileName);
    return Files.isDirectory(path);
  }
}