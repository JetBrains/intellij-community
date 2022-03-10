// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  long isNewFile(File file, long lastModified) {
    System.out.println(file.isFile());
    while (file.isDirectory()) {
        System.out.println(file.i<caret>sFile());
        System.out.println(file.length());
    }
    return file.lastModified();
  }
}