import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class C {
  void m(File file) throws IOException {
      Ob<caret>ject broken;
      try (var res = new FileInputStream(file)) {
          broken = null;
          res.read();
      }
      System.out.println(broken);
  }
}