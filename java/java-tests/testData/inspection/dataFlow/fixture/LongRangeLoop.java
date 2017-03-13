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
