import java.util.*;

@FunctionalInterface
interface InfiniteIntIterator extends InfiniteIterator<Integer>, PrimitiveIterator.OfInt {}

@FunctionalInterface
interface InfiniteIterator<T> extends Iterator<T> {
  @Override
  default boolean hasNext() {
    return true;
  }
}