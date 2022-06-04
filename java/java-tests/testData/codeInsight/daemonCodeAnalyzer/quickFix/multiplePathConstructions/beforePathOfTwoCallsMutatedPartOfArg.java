// "Extract 'java.nio.file.Path' constructions to variable" "false"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(String start, String end) {
    if (Path.of((start.<caret>length() > 2 ? (start + end) : ("baz"))).isAbsolute()) return false;
    if (end.length() > 2) end = "fo";
    return Files.isDirectory(Path.of((start.length() > 2 ? (start + end) : ("baz"))));
  }
}