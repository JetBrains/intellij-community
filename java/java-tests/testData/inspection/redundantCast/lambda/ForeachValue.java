import java.util.Arrays;
import java.util.Iterator;

class IterableMain {
  public static void main(final String... args) {
    for (final String s :  (Iterable<String>) <error descr="'XIterator' is abstract; cannot be instantiated">XIterator::new</error>) {}
  }

  public static interface XIterator extends Iterator<String> {}
}