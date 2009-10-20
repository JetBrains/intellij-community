import java.io.InputStream;
class Test {
  InputStream inputStream = new MyIn<caret>

  private class MyInputStream extends InputStream {
    MyInputStream(String nmame){}
    public int read() throws IOException {
      return 0;
    }
  }
}
