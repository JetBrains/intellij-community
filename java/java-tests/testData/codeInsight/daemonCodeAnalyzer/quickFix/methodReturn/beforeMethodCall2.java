// "Make 'getNumber()' return 'double'" "true-preview"
public class Test {
  int number;

  public int getNumber() {
    return number;
  }

  public void consumeString(String s, int s1) {
    // Do nothing
  }

  public void consumeString(Double s, int s1) {
    // Do nothing
  }

  public Test() {
    consumeString(getNu<caret>mber(), 1);
  }
}
