import java.util.function.Supplier;

class LazyVal<T> {
  public LazyVal(Supplier<T> supplier) {}
  public LazyVal(T value) {}
}

class Sample {

  String getString() {
    return "";
  }

  public void usage() {
    new LazyVal<>(() -> new Sample().getString());
  }
}
