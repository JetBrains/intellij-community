// "Replace with bulk 'Files.readAttributes()' call" "true-preview"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  
  private File file;
  
  boolean printDirectory() {
    try {
        var basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return basicFileAttributes.isDirectory() && basicFileAttributes.isRegularFile();
    } catch (Exception e) {
    }
    return false;
  }
}