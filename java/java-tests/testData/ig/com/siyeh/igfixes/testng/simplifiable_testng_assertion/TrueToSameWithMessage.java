import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
    assert<caret>True(foo() == foo(), "message");
  }

  private Object foo() {
    return null;
  }
}
