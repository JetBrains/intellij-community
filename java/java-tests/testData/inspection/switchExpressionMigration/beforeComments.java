// "Replace with enhanced 'switch' statement" "true"
class X {
  private static void test(String... commands) {
    for (String command : commands) {
      swit<caret>ch (command) {
        case "Say hello":
          // line contains no height
          System.out.printf("Hello, %s!%n", System.getProperty("user.name"));
          break;
        case "Exit":
          // line contains no code
          System.out.println("Goodbye.");
          break;
        case "Multiline":
          // a
          System.out.println("a");
          // b
          System.out.println("b");
          // c
          System.out.println("c");
          break;
        default:     // line contains code at second position and height
          System.out.println(command);
          break;
      }
    }
  }
}