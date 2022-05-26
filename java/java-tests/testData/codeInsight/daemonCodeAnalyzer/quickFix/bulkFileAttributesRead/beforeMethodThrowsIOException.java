// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  long isNewFile(File file) throws IOException {
    while (file.isDire<caret>ctory()) {
      System.out.println(file.isFile());
    }
    return file.lastModified();
  }
}