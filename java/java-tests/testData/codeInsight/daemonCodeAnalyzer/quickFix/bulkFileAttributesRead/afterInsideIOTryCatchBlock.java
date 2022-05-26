// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  boolean printDirectory(File file) {
    try {
        BasicFileAttributes basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return basicFileAttributes.isDirectory() && basicFileAttributes.isRegularFile();
      throw new IOException("");
    } catch (IOException e) {
    }
  }
}