import java.io.*;
import java.nio.file.*;

class C {
  public boolean isRelativeDirectory() {
    String fileName = "foo";
    if (Paths.get(fileName<caret>).isAbsolute()) return false;
    System.out.println(fileName);
    return Files.isDirectory(Paths.get(fileName));
  }
}