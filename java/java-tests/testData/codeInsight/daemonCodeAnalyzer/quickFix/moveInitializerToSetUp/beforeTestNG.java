// "Move initializer to setUp method" "true"
public class X {
  <caret>int i = 7;

  @org.testng.annotations.Test
  public void test() {
  }
}
