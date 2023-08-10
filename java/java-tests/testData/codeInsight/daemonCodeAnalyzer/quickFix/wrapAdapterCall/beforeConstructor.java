// "Adapt argument using 'new File()'" "true-preview"
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