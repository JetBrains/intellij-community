// "Replace with bulk 'Files.readAttributes' call" "false"
import java.io.*;

class Foo {
  void printDirectory(File file, long lastModified) {
    while (file.isDire<caret>ctory()) {
      System.out.println(file.isDirectory());
    }
  }
}