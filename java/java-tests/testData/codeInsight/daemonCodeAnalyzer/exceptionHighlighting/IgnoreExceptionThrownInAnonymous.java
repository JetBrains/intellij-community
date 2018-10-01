import java.io.IOException;
import java.util.concurrent.Callable;

class MyTest {
  public static void main() {
    try {
      new Callable() {
        @Override
        public Object call() throws IOException {
          throw new IOException();
        }
      };
      
      new Foo("") {
        @Override
        public Object call() throws IOException {
          throw new IOException();
        }
      };
    }
    catch (Exception e) {
      throw e;
    }
  }
  
  static abstract class Foo implements Callable {
    protected Foo(Object o) {
    }
  }
}
