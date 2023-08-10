import java.util.*;
import java.util.function.*;
import java.util.stream.*;

class Demo {
  void test() {
    List<Integer> list = Arrays.asList(1, 2, 3);
    List<Integer> target = new ArrayList<>();
    from(list).toList(target);
    System.out.println(target.isEmpty());
  }

  static <T> SmartStream<T> from(Collection<T> collection) {
    return asSmart(collection.stream());
  }

  static <T> SmartStream<T> asSmart(Stream<T> stream) {
    if (stream instanceof SmartStream<?>) {
      return ((SmartStream<T>) stream);
    } else {
      return new SmartStream<>(stream);
    }
  }

  static class SmartStream<T> implements Stream<T> {
    private final Stream<T> stream;

    protected SmartStream(Stream<T> stream) {
      this.stream = stream;
    }

    public <C extends List<T>> C toList(C destination) {
      return stream.collect(Collectors.toCollection(() -> destination));
    }

    @Override
    public SmartStream<T> filter(Predicate<? super T> predicate) {
      return asSmart(stream.filter(predicate));
    }

    @Override
    public <R> SmartStream<R> map(Function<? super T, ? extends R> mapper) {
      return asSmart(stream.map(mapper));
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
      return stream.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
      return stream.mapToLong(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
      return stream.mapToDouble(mapper);
    }

    @Override
    public <R> SmartStream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
      return asSmart(stream.flatMap(mapper));
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
      return stream.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
      return stream.flatMapToLong(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
      return stream.flatMapToDouble(mapper);
    }

    @Override
    public SmartStream<T> distinct() {
      return asSmart(stream.distinct());
    }

    @Override
    public SmartStream<T> sorted() {
      return asSmart(stream.sorted());
    }

    @Override
    public SmartStream<T> sorted(Comparator<? super T> comparator) {
      return asSmart(stream.sorted(comparator));
    }

    @Override
    public SmartStream<T> peek(Consumer<? super T> action) {
      return asSmart(stream.peek(action));
    }

    @Override
    public SmartStream<T> limit(long maxSize) {
      return asSmart(stream.limit(maxSize));
    }

    @Override
    public SmartStream<T> skip(long n) {
      return asSmart(stream.skip(n));
    }

    @Override
    public void forEach(Consumer<? super T> action) {
      stream.forEach(action);
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
      stream.forEachOrdered(action);
    }

    @Override
    public Object[] toArray() {
      return stream.toArray();
    }

    @SuppressWarnings("SuspiciousToArrayCall")
    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
      return stream.toArray(generator);
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
      return stream.reduce(identity, accumulator);
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
      return stream.reduce(accumulator);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
      return stream.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
      return stream.collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
      return stream.collect(collector);
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
      return stream.min(comparator);
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
      return stream.max(comparator);
    }

    @Override
    public long count() {
      return stream.count();
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
      return stream.anyMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
      return stream.allMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
      return stream.noneMatch(predicate);
    }

    @Override
    public Optional<T> findFirst() {
      return stream.findFirst();
    }

    @Override
    public Optional<T> findAny() {
      return stream.findAny();
    }

    @Override
    public Iterator<T> iterator() {
      return stream.iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
      return stream.spliterator();
    }

    @Override
    public boolean isParallel() {
      return stream.isParallel();
    }

    @Override
    public SmartStream<T> sequential() {
      return asSmart(stream.sequential());
    }

    @Override
    public SmartStream<T> parallel() {
      return asSmart(stream.parallel());
    }

    @Override
    public SmartStream<T> unordered() {
      return asSmart(stream.unordered());
    }

    @Override
    public SmartStream<T> onClose(Runnable closeHandler) {
      return asSmart(stream.onClose(closeHandler));
    }

    @Override
    public void close() {
      stream.close();
    }
  }
}