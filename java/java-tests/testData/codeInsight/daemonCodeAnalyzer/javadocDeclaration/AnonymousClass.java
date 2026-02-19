import java.util.Iterator;

class Main {
  {
    new Iterable<Integer>() {
      // comment

      @Override
      public Iterator<Integer> iterator() {
        return null;
      }
    };
  }
}
