// "Replace with 'Files.newInputStream'" "false"
import java.io.*;

class Foo {
  void test(String str) {
    try(InputStream in = new FileInputStream(s<caret>tr)) {
    }
    catch (FileNotFoundException | RuntimeException e) {
      System.out.println("file not found exception");
    }
    catch (IOException e) {
      System.out.println("Don't know what happened");
    }
  }
}