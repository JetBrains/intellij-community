import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class C {
  void m(File file) throws IOException {
    var <caret>res = new FileInputStream(file);
    var broken = null;
    res.read();
    System.out.println(broken);
  }
}