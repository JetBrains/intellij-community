// "Suppress for statement" "true"
public class Test {
  public void run() {
    @SuppressWarnings({"UnusedDeclaration"}) int <caret>i;
  }
}