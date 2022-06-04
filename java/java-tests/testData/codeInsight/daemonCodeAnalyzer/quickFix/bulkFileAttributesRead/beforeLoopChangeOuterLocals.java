// "Replace with bulk 'Files.readAttributes' call" "true"
import java.io.*;

class Foo {
  void printWhileDirectory(File file) {
    System.out.println(file<caret>.isFile());
    System.out.println(file.lastModified());
    while (file.isDirectory()) {
      System.out.println(file.isFile());
      System.out.println(file.length());
    }
  }
}