public class SplitCondition {
  private static void appendString(String phrase) {
      if (phrase != null) {
          if (phrase.contains("abc")) {
              System.out.println("abc!");
          }
      } else {
          System.out.println("null");
      }
  }
}
