public class SplitCondition {
  private static void appendString(StringBuilder builder, boolean condition) {
    if (condition&<caret>&builder.length() > 0) {
    }
  }
}
