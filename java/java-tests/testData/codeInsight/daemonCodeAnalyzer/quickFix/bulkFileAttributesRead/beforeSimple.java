// "Replace with bulk 'Files.readAttributes()' call" "true-preview"
import java.io.*;

class Foo {
  long isNewFile(File file, long lastModified) {
    if (file.isDirecto<caret>ry()) {
      System.out.println(file.isFile());
    }
    return file.lastModified();
  }
}