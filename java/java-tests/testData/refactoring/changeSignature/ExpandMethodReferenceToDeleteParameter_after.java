import java.util.ArrayList;
import java.util.List;

interface P {
  boolean m(Integer i);
}

class A {
  public static void print() {
    List<Integer> someNumbers = A.returnAllNumbers((a) -> alwaysTrue());
  }

  private static List<Integer> returnAllNumbers(P predicate) {
    return new ArrayList<>();
  }

  public static boolean alwaysTrue() {
    return true;
  }
}