// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

class Foo {
  long isNewFile(File file, long lastModified) {
    System.out.println(file.isFile());
    while (file.isDirectory()) {
        BasicFileAttributes basicFileAttributes;
        try {
            basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println(basicFileAttributes.isRegularFile());
        System.out.println(basicFileAttributes.size());
    }
    return file.lastModified();
  }
}