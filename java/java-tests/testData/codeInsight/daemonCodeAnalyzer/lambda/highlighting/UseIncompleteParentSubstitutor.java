import java.util.concurrent.atomic.AtomicInteger;

class Test {
  static class List<T> {
    public Stream<T> stream() {
      return null;
    }
  }

  interface IntFunction<T> {
      public int applyAsInt(T t);
  }

  static class Stream<E> {
    public Stream map(IntFunction<? super E> mapper) {
      return null;
    }
  }

  public static void main(List<AtomicInteger> list) {
    list.stream().map(atomic -> atomic.get());
  }
}


