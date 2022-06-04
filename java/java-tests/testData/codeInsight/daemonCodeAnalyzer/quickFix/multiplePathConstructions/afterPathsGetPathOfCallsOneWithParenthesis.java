// "Extract 'java.nio.file.Path' constructions to variable" "true"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(String start, String end) {
      Path of = Path.of(start.length() > 2 ? start + end : "baz");
      if (of.isAbsolute()) return false;
      /*1*/
      /*2*/
      /*3*/
      return Files.isDirectory(/*0*/of);
  }
}