// "Replace with 'switch' expression" "false"

public class EnhancedSwitchIntentionBug {
  public static void main(String[] args) {
    int x;
    switch<caret> (args[0]) {
      case "1":
      case "2":
      case "3":
      default:
    }
  }
}
