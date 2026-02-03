public class SplitCondition {
  private static void appendString(String phrase) {
    if (phrase != null &<caret>& phrase.contains("abc")) {
      System.out.println("abc!");
    }
    else if (phrase == null) {
      System.out.println("null");
    }
  }
}
