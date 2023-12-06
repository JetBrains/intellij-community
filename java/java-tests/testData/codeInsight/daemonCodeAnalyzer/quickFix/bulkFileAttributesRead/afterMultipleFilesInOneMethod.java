// "Fix all 'Bulk 'Files.readAttributes()' call can be used' problems in file" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  void printDirectory(File file1, File file2) throws IOException {
      BasicFileAttributes fileAttributes = Files.readAttributes(file1.toPath(), BasicFileAttributes.class);
      if (fileAttributes.isDirectory()) {
      System.out.println(fileAttributes.size());
    }
      BasicFileAttributes basicFileAttributes = Files.readAttributes(file2.toPath(), BasicFileAttributes.class);
      if (basicFileAttributes.isRegularFile()) {
      System.out.println(basicFileAttributes.size());
    }
  }
}