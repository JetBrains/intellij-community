// "Fix all 'Bulk 'Files.readAttributes' call can be used instead of multiple file attribute calls' problems in file" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  void printDirectory(File file1, File file2) throws IOException {
      BasicFileAttributes readAttributes1 = Files.readAttributes(file1.toPath(), BasicFileAttributes.class);
      if (readAttributes1.isDirectory()) {
      System.out.println(readAttributes1.size());
    }
      BasicFileAttributes readAttributes = Files.readAttributes(file2.toPath(), BasicFileAttributes.class);
      if (readAttributes.isRegularFile()) {
      System.out.println(readAttributes.size());
    }
  }
}