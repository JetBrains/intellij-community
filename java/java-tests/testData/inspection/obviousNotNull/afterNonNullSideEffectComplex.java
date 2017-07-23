// "Fix all 'Null-check method is called with obviously non-null argument' problems in file" "true"
import java.util.Objects;

public class Test {
  Test(int i) {
  }

  public static void testTernaryLeft() {
      if (args.length > 0) {
          new Test(1);
          new Test(2);
      }
  }

  public static void testTernaryRight() {
      if (args.length <= 0) {
          new Test(2);
          new Test(3);
      }
  }

  public static void testTernaryBoth() {
      if (args.length > 0) {
          new Test(1);
      } else {
          new Test(2);
          new Test(3);
      }
  }

  public static void testAndTernarySimply() {
      new Test(1).hashCode();
  }

  public static void testAndTernaryBranch() {
      if (new Test(1).hashCode() <= 0 || args.length <= 0) {
          new Test(2);
      }
  }

  public static void testAndBoth() {
      if (new Test(1).hashCode() > 0) {
          new Test(2).hashCode();
      }
  }

  public static void testAndTwoOfThree() {
      if (new Test(1).hashCode() > 0) {
          new Test(2).hashCode();
      }
  }

  public static void testAndTwoOfThreePlusBranch() {
      if (new Test(1).hashCode() > 0 && new Test(2).hashCode() > 0
              && args.length > 0) {
          new Test(3).toString();
      }
  }

  public static void testAndOrMixed() {
      if (new Test(1).hashCode() <= 0 || new Test(2).hashCode() <= 0) {
          if (new Test(3).hashCode() + new Test(4).hashCode() > 1) {
              new Test(5).hashCode();
              new Test(6).hashCode();
          }
      }
  }
}