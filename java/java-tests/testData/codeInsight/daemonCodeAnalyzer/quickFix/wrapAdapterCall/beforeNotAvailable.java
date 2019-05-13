// "Wrap parameter using 'new File()'" "false"
import java.io.File;

class Test {

  void m(CharSequence sequence) {
    readFile(sequen<caret>ce);
  }

  static String readFile(File f) {
    return null;
  }

}