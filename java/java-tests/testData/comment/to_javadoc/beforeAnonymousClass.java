// "Replace with javadoc" "false"
import java.util.Iterator;

class Main {
  {
    new Iterable<Integer>() {
      // <caret>comment

      @Override
      public Iterator<Integer> iterator() {
        return null;
      }
    };
  }
}
