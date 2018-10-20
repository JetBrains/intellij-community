// "Create type parameter 'T'" "false"

public class Test {
  private enum State {
    VALID, INVALID;
    private T<caret> info;
  }
}