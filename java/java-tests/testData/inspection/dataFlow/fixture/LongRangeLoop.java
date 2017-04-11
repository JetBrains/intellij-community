import java.util.Collection;

public class LongRangeLoop {
  public static int min(int[] values) {
    int min = Integer.MAX_VALUE;
    for (int value : values) {
      if (value < min) min = value;
    }
    if (<warning descr="Condition 'min > 0 && min < -10' is always 'false'">min > 0 && <warning descr="Condition 'min < -10' is always 'false' when reached">min < -10</warning></warning>) {
      System.out.println("Invalid result");
    }
    return min;
  }

  public static void boundedForLoop() {
    for(int i=0; i<10; i++) {
      if(<warning descr="Condition 'i == 20' is always 'false'">i == 20</warning>) {
        System.out.println("Oops");
      }
      if(<warning descr="Condition 'i < -10' is always 'false'">i < -10</warning>) {
        System.out.println("Oops");
      }
    }
  }

  public static void loopOrigin(long i) {
    if(i > 0) {
      for (long j = i; j <= 1000; ++j) {
        if (<warning descr="Condition 'j < 0' is always 'false'">j < 0</warning>) {
          System.out.println("Impossible");
        }
      }
    }
  }

  public static void loopUnknownBoundIncluding(int bound) {
    for (int i = 0; i <= bound; i++) {
      if (i == -1) {
        System.out.println("Overflow detected: bound was Integer.MAX_VALUE");
      }
    }
  }

  public static void loopUnknownBoundExcluding(int bound) {
    for (int i = 0; i < bound; i++) {
      if (<warning descr="Condition 'i == -1' is always 'false'">i == -1</warning>) {
        System.out.println("Impossible even if bound is Integer.MAX_VALUE");
      }
    }
  }

  public static void loopUnknownBoundIncludingLong(int bound) {
    for (long i = 0; i <= bound; i++) {
      if (<warning descr="Condition 'i == -1' is always 'false'">i == -1</warning>) {
        System.out.println("Impossible even if bound is Integer.MAX_VALUE");
      }
    }
  }

  public void IDEA168715() {
    for (int i = 1; i < 100; i++) {
      if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {
        System.out.print("Dead code");
      }
    }
  }

  public void testVariablesAreInDeclarationOrder(String file, Collection<String> vars) throws Exception {
    int previousIndex = -1;
    for (String each : vars) {
      int index = file.indexOf(each);
      if(index <= previousIndex) {
        throw new AssertionError();
      }
      previousIndex = index;
      if(<warning descr="Condition 'index == -1' is always 'false'">index == -1</warning>) {
        System.out.println("Impossible");
      }
    }
  }
}
