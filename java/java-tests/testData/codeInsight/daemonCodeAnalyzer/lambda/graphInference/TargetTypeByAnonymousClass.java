import java.util.function.Function;

class TypeDetectionTest {

  public static void main(String[] args) {
    new Table<String>(Function.identity()) {{}};
    new Table<String>(Function.identity());
    new Table<String>(x -> x) {{}};

  }

  public static class Table<T> {
    public Table(Function<T, T> f) {
    }
  }
}