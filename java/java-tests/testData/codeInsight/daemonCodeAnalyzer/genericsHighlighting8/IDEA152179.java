import java.util.*;
class Test {
  public static void foo(final List<? extends Comparable> comparables) {
    Collections.sort(comparables);
  }
}