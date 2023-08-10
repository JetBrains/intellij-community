import java.io.Reader;

class X {
  public void test() throws Exception {
    Reader r = new FileReader("");

    <caret>try {
      r.read();
    }
    finally {
      r.close();
    }
  }
}