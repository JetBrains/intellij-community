// "Replace with bulk 'Files.readAttributes' call" "false"
import java.io.*;

class Foo {
  long isNewFile(File file, long lastModified) {
    System.out.println(file.isFile());
    System.out.println(file.length());
    while (file.isDirectory()) {
        System.out.println(file.i<caret>sFile());
    }
    return file.lastModified();
  }
}