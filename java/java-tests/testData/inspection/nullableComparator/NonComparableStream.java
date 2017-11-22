import java.util.stream.*;

class Main {
  static class A{}

  public static void main(String[] args) {
    Stream.of(new A(), new A()).sorted(<warning descr="Nullable comparator passed to sort method on elements not implementing Comparable">null</warning>);
  }
}