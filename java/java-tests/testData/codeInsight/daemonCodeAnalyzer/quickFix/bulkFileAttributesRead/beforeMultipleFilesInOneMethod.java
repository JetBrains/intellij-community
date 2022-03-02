// "Fix all 'Bulk 'Files.readAttributes' call can be used instead of multiple file attribute calls' problems in file" "true"
import java.io.*;

class Foo {
  void printDirectory(File file1, File file2) throws IOException {
    if (file1.isD<caret>irectory()) {
      System.out.println(file1.length());
    }
    if (file2.isFile()) {
      System.out.println(file2.length());
    }
  }
}