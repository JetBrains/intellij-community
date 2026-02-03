import java.io.FileInputStream;
import java.io.IOException;

class Test {
  void foo() {
    try {
      try (FileInputStream in = new FileInputStream("adsf")) {
      }
    } catch (IOException <caret>) {}
  }
}