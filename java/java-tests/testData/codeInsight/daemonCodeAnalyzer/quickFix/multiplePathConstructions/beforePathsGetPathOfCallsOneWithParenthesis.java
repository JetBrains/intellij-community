// "Extract 'java.nio.file.Path' constructions to variable" "true"
import java.io.*;
import java.nio.file.*;

class Foo {
  public boolean isRelativeDirectory(String start, String end) {
    if (Path.of(start.le<caret>ngth() > 2 ? start + end : "baz").isAbsolute()) return false;
    return Files.isDirectory(/*0*/Paths.get(((((start/*1*/).length()) > 2) ? ((start)/*2*/ + end) : ("baz"))/*3*/));
  }
}