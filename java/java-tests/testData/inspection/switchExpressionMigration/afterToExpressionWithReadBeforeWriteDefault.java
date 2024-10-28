// "Replace with 'switch' expression" "true-preview"
public final class A {
  private void appendColored(String text) {
    String style = getRegularAttributes();
      style = switch (text) {
          case "failed" -> "ERROR_ATTRIBUTES";
          case "ignored" -> "IGNORE_ATTRIBUTES";
          default -> style;
      };
  }

  private static String getRegularAttributes() {
    return "REGULAR_ATTRIBUTES";
  }
}