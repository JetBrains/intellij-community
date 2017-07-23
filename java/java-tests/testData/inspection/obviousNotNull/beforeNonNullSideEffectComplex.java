// "Fix all 'Null-check method is called with obviously non-null argument' problems in file" "true"
import java.util.Objects;

public class Test {
  Test(int i) {
  }

  public static void testTernaryLeft() {
    Objects.requireNonNull("xyz" +<caret> (args.length > 0 ? new Test(1) + ":" + new Test(2) : ""));
  }

  public static void testTernaryRight() {
    Objects.requireNonNull("xyz" + (args.length > 0 ? "null" : new Test(2)+":"+new Test(3)));
  }

  public static void testTernaryBoth() {
    Objects.requireNonNull("xyz" + (args.length > 0 ? new Test(1) : new Test(2)+":"+new Test(3)));
  }

  public static void testAndTernarySimply() {
    Objects.requireNonNull("xyz" + (new Test(1).hashCode() > 0 && args.length > 0 ? "x" : "y"));
  }

  public static void testAndTernaryBranch() {
    Objects.requireNonNull("xyz" + (new Test(1).hashCode() > 0 && args.length > 0 ? "x" : new Test(2)));
  }

  public static void testAndBoth() {
    Objects.requireNonNull("xyz" + (new Test(1).hashCode() > 0 && new Test(2).hashCode() > 0 ? "x" : "y"));
  }

  public static void testAndTwoOfThree() {
    Objects.requireNonNull("xyz" + (new Test(1).hashCode() > 0 && new Test(2).hashCode() > 0
                                    && args.length > 0 ? "x" : "y"));
  }

  public static void testAndTwoOfThreePlusBranch() {
    Objects.requireNonNull("xyz" + (new Test(1).hashCode() > 0 && new Test(2).hashCode() > 0
                                    && args.length > 0 ? new Test(3).toString() : "y"));
  }

  public static void testAndOrMixed() {
    Objects.requireNonNull("xyz" + (new Test(1).hashCode() > 0 && new Test(2).hashCode() > 0
                                    || new Test(3).hashCode() + new Test(4).hashCode() > 1 &&
                                       new Test(5).hashCode() + new Test(6).hashCode() > 2));
  }
}