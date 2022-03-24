// "Replace with bulk 'Files.readAttributes' call" "false"
import java.io.*;

class Foo {
  long isNewFile(File file, long lastModified) {
    while (file.isDir<caret>ectory()) {
      System.out.println(file.isFile());
    }
    file = new File("foo");
    return file.lastModified();
  }
}