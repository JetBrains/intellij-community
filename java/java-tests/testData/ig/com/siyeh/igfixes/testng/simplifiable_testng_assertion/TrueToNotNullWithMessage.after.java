import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertNotNull(foo(), "message");
  }

  private Object foo() {
    return null;
  }
}
