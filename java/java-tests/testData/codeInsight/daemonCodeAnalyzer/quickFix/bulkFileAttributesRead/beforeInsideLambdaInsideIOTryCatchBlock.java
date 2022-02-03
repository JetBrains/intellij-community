// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  void printDirectory(File file) {
    try {
      Callable<Boolean> r = () -> file.isDirec<caret>tory() && file.isFile();
      throw new IOException("");
    } catch (IOException e) {
    }
  }
}