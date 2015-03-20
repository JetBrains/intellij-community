import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.Callable;

class Test  {
  public static final void main(String[] args) throws Exception {
    Reader r = new StringReader("Elvis lives!");
    Callable<Integer >  c1 = () -> {
      while (true) {
        r.read();
      }
    };
  }
}
