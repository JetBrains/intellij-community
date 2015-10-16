


import java.io.File;
import java.io.IOException;
interface I {
  void m(String s) throws IOException;
}
class Test {
  public void test() throws IOException {
    File file = new File("temp");
    try {
      for(int t = 0; t < 4; ++t) {
        for (int i = 0; i < 10; ++i) {
          I appender = out -> File.createTempFile("", "").exists();
          appender.m("");
        }
      }
    }
    finally {
      System.out.println(file);
    }
  }
}