// "Adapt argument using 'new File()'" "true-preview"
import java.io.File;

class Test {

  void m() {
    readFile("my<caret>.txt");
  }

  static String readFile(File f) {
    return null;
  }

}