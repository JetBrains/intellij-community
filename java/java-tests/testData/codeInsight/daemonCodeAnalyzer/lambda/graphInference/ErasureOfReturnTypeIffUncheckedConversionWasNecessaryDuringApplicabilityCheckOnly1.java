
import java.util.function.Function;
import java.util.function.Supplier;

class Thing<T> {

  private final T value;

  public Thing(T value) {
    this.value = value;
  }

  public <U> Thing<U> flatMap(Function<T, Thing<U>> f) {
    return f.apply(value);
  }

  public <X extends Throwable> void exceptionMethod(Supplier<X> xSupplier) throws X { }

  void m(final Thing<Integer> integer){
    integer.flatMap(s -> new Thing(s)).<error descr="Unhandled exception: java.lang.Throwable">exceptionMethod(IllegalStateException::new);</error>
    integer.flatMap(Thing::new).exceptionMethod(IllegalStateException::new);
  }
}