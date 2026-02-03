// "Move initializer to setUp method" "false"
public class X {
  <caret>int i = 7;

  @org.testng.annotations.Test
  public void test() {
  }
}
