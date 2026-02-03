public class Convert2AtomicTest {
  public boolean f<caret>oo() {
    Processor processor = new Processor() {
      @Override
      public boolean process(Object o) {
        return false; // Changes false to true here, WRONG!!!
      }
    };

    return false; // Changes false to true here, right
  }
}

interface Processor {
  boolean process(Object object);
}