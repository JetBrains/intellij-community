import java.util.ArrayList;
import java.util.List;

interface P {
  boolean m(Integer i);
}

class A {
  public static void print() {
    List<Integer> someNumbers = A.returnAllNumbers(A::alwaysTrue);
  }

  private static List<Integer> returnAllNumbers(P predicate) {
    return new ArrayList<>();
  }

  public static boolean alwa<caret>ysTrue(int a) {
    return true;
  }
}