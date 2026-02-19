import java.io.Reader;

class X {
  public void test() throws Exception {

      try (Reader r = new FileReader("")) {
          r.read();
      }
  }
}