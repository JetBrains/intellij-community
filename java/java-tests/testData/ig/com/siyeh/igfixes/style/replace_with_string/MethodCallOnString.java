public class MethodCallOnString {
  public static void main(String... args) {
    StringBuilder theBuilder<caret> = new StringBuilder("ABC");
    int length = theBuilder.toString().length();
  }
}