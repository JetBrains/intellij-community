// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  long isNewFile(File file, long lastModified) {
      BasicFileAttributes basicFileAttributes;
      try {
          basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
      while (basicFileAttributes.isDirectory()) {
      System.out.println(basicFileAttributes.isRegularFile());
    }
    return basicFileAttributes.lastModifiedTime().toMillis();
  }
}