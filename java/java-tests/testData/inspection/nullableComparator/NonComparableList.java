import java.util.ArrayList;

class Main {
  static class A {}

  public static void main(String[] args) {
    new ArrayList<A>().sort(<warning descr="Nullable comparator passed to sort method on elements not implementing Comparable">null</warning>);
  }
}