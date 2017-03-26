// "Wrap 2nd parameter using 'new File()'" "true"
import java.io.File;

class Test {

  void m() {
    readFile(0, "m<caret>y.txt", 2);
  }

  static String readFile(int additionalParameter1, File f, int additionalParameter2) {
    return null;
  }

}