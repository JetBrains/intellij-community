// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  void printWhileDirectory(File file) {
      BasicFileAttributes basicFileAttributes;
      try {
          basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      } catch (IOException e) {
          throw new UncheckedIOException(e);
      }
      System.out.println(basicFileAttributes.isRegularFile());
    System.out.println(basicFileAttributes.lastModifiedTime().toMillis());
    while (file.isDirectory()) {
      System.out.println(file.isFile());
      System.out.println(file.length());
    }
  }
}