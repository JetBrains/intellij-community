// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  boolean printDirectory(File file) {
    try {
      return file.isDirec<caret>tory() && file.isFile();
      throw new IOException("");
    } catch (IOException e) {
    }
  }
}