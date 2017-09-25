// "Wrap parameter using 'new File()'" "true"
import java.io.File;

class Test {

  void m() {
    new FileReader("m<caret>y.txt");
  }
}

class FileReader {
  public FileReader(File file) {
  }
}