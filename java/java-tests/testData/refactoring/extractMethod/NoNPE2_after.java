import java.util.Iterator;
public class ConcatIterables {
  class ConcatenatingIterable<A> implements Iterable<A> {
    ImmutableQueue<Iterable<A>> iterables;
    public ConcatenatingIterable(Iterable<A> xs, Iterable<A> ys) {
      newMethod(xs, (ConcatenatingIterable<A>) ys);
    }

      private ImmutableQueue<Iterable<A>> newMethod(Iterable<A> xs, ConcatenatingIterable<A> ys) {
          return ys.iterables.pushFront(xs);
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