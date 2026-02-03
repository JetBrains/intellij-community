// "Adapt argument using 'new File()'" "true-preview"
import java.io.File;

class Test {

  void m() {
    new FileReader(new File("my.txt"));
  }
}

class FileReader {
  public FileReader(File file) {
  }
}