// "Wrap parameter using 'new File()'" "true"
import java.io.File;

class Test {

  void m() {
    readFile(new File("my.txt"));
  }

  static String readFile(File f) {
    return null;
  }

}