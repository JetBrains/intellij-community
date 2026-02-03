import java.io.FileInputStream;

class Test {
  void foo() {
    try {
      try (FileInputStream in = new FileInputStream("adsf")) {
      }
    } catch (IO<caret>) {}
  }
}