// "Make 'getNumber()' return 'java.lang.String'" "true-preview"
public class Test {
  int number;

  public String getNumber() {
    return number;
  }

  public void consumeString(String s, int s1) {
    // Do nothing
  }

  public void consumeString(Double s, int s1) {
    // Do nothing
  }

  public Test() {
    consumeString(getNumber(), 1);
  }
}
