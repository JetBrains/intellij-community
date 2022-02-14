// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  long isNewFile(File file, long lastModified) {
    while (file.isDirectory()) {
      System.out.println(file.isFile());
      for (int i = 0; i < 10 && file.isDirectory(); i++) {
        System.out.println(file.i<caret>sFile());
        System.out.println(file.length());
      }
    }
    return file.lastModified();
  }
}