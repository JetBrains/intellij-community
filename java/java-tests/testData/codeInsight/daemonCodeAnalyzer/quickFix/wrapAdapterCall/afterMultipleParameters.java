// "Adapt 2nd argument using 'new File()'" "true-preview"
import java.io.File;

class Test {

  void m() {
    readFile(0, new File("my.txt"), 2);
  }

  static String readFile(int additionalParameter1, File f, int additionalParameter2) {
    return null;
  }

}