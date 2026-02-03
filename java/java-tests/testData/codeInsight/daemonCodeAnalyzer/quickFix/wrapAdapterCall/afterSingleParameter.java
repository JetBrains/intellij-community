// "Adapt argument using 'new File()'" "true-preview"
import java.io.File;

class Test {

  void m() {
    readFile(new File("my.txt"));
  }

  static String readFile(File f) {
    return null;
  }

}