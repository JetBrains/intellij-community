// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  long isNewFile(File file, long lastModified) {
    while (file.isDirectory()) {
      System.out.println(file.isFile());
      for (int i = 0; i < 10 && file.isDirectory(); i++) {
          BasicFileAttributes basicFileAttributes;
          try {
              basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
          } catch (IOException e) {
              throw new UncheckedIOException(e);
          }
          System.out.println(basicFileAttributes.isRegularFile());
        System.out.println(basicFileAttributes.size());
      }
    }
    return file.lastModified();
  }
}