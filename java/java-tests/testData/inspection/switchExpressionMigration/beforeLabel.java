// "Replace with enhanced 'switch' statement" "false"

public class EnhancedSwitchIntentionBug {
  public static void main(String... args) {
    test("Say hello", "Exit", "Hello, bug!");
  }

  private static void test(String... commands) {
    loopLabel:
    for (String command : commands) {
      switch<caret> (command) {
        case "Say hello":
          System.out.printf("Hello, %s!%n", System.getProperty("user.name"));
          break;
        case "Exit":
          System.out.println("Goodbye.");
          break loopLabel;
        default:
          System.out.println(command);
          break;
      }
    }
  }
}