// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  
  private File file;
  
  boolean printDirectory() {
    try {
      return file.isDirec<caret>tory() && file.isFile();
      throw new IOException("");
    } catch (IOException e) {
    }
  }
}