
import java.io.FileNotFoundException;
import java.io.IOException;

class Outer {
  private interface IA {
    void print() throws FileNotFoundException, IOException;
  }

  private static class A implements IA {
    public void print() throws FileNotFoundException, IOException {
    }
  }

  private static class B extends A {
      @Override
      public void print() throws FileNotFoundException, IOException {
          super.print();
      }
  }
}