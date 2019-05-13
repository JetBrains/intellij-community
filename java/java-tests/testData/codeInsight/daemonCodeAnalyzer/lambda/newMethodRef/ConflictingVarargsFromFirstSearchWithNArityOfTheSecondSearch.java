import java.util.function.BinaryOperator;

class Test {
  static class Base<T> {
    Child<T> prepend(Child<T> other) {
      return null;
    }
  }

  static class Child<T> extends Base<T> {
    @SafeVarargs
    final Child<T> prepend(T... args) {
      return this;
    }

    Child<T> prepend(T arg) {
      return this;
    }
  }

  public static void main(String[] args) {
    BinaryOperator<Child<String>> prepend = Child::prepend;
  }
}