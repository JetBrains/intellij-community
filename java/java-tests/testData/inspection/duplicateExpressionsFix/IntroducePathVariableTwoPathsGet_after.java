import java.io.*;
import java.nio.file.*;

class C {
  public boolean isRelativeDirectory() {
    String fileName = "foo";
      Path of<caret> = Path.of(fileName);
      if (of.isAbsolute()) return false;
    System.out.println(fileName);
    return Files.isDirectory(of);
  }
}