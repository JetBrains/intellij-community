// "Replace with 'switch' expression" "true-preview"
public final class A {
  private void appendColored(String text) {
    String style = getRegularAttributes();
    switc<caret>h (text) {
      case "failed":
        style = "ERROR_ATTRIBUTES";
        break;
      case "ignored":
        style = "IGNORE_ATTRIBUTES";
        break;
    }
  }

  private static String getRegularAttributes() {
    return "REGULAR_ATTRIBUTES";
  }
}