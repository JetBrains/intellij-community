import java.util.Arrays;
import java.util.Iterator;

class IterableMain {
  public static void main(final String... args) {
    for (final String s :  (Iterable<String>) XIterator::new) {}
  }

  public static interface XIterator extends Iterator<String> {}
}