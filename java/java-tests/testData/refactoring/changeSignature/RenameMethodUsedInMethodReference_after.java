import java.util.ArrayList;
import java.util.List;

interface P {
  boolean m(Integer i);
}

class A {
  public static void print() {
    List<Integer> someNumbers = A.returnAllNumbers(A::alwaysFalse);
  }

  private static List<Integer> returnAllNumbers(P predicate) {
    return new ArrayList<>();
  }

  private static boolean alwaysFalse(int a) {
    return true;
  }
}