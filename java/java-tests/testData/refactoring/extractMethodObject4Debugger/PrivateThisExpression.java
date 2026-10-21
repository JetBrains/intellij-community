public class PrivateThisExpression {
  private static class Inner {
    private void run() {
      <caret>int marker = 0;
    }
  }
}
