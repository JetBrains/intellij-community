import typeUse.*;

class X {
  static abstract class Base<T> {
    abstract void consume(T item);
  }

  static class Child extends Base<@NotNull String> {
    @Override
    void consume(@NotNull String item) {
      System.out.println(item);
    }
  }

  public static void main(String[] args) {
    Base<@NotNull String> child = new Child();
    child.consume("this can't be null");
    child.consume(null);
  }
}