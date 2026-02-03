interface Stream<T> {
  void forEach(Consumer<? super T> consumer);
}

interface List<T> {
  Stream<T> stream();
}

interface Consumer<T> {
  public void accept(T t);
}

interface BiFunction<T, U, R> {
  R apply(T t, U u);
}

interface BiConsumer<T, U> {
  void accept(T t, U u);
}

class Test {
  public static <A, B> void zipConsume(Stream<? extends A> a, Stream<? extends B> b,
                                       BiConsumer<? super A, ? super B> zipper) {
    zip(a, b, Pair<A, B>::new).forEach(p -> zipper.accept(p.a, p.b));
  }

  public static <A, B, C> Stream<C> zip(Stream<? extends A> a,
                                        Stream<? extends B> b,
                                        BiFunction<? super A, ? super B, ? extends C> zipper) {
    return null;
  }

  private static class Pair<A, B> {
    private final A a;
    private final B b;

    protected Pair(A a, B b) {
      this.a = a;
      this.b = b;
    }
  }

  void foo(List<Integer> a, List<Integer> b) {
    zipConsume(a.stream(), b.stream(), (x, y) -> System.out.println(x + y));
  }
}