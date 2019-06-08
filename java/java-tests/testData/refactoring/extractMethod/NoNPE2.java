import java.util.Iterator;
public class ConcatIterables {
  class ConcatenatingIterable<A> implements Iterable<A> {
    ImmutableQueue<Iterable<A>> iterables;
    public ConcatenatingIterable(Iterable<A> xs, Iterable<A> ys) {
      <selection>((ConcatenatingIterable<A>) ys).iterables.pushFront(xs)</selection>;
    }
    @Override
    public Iterator<A> iterator() {
      return null;
    }
  }
  static class ImmutableQueue<A> implements Iterable<A> {
    public static <A> ImmutableQueue<A> empty() {
      return new ImmutableQueue<>();
    }
    @Override
    public Iterator<A> iterator() {
      return null;
    }
    ImmutableQueue<A> pushFront(A a) {
      return null;
    }
  }
}