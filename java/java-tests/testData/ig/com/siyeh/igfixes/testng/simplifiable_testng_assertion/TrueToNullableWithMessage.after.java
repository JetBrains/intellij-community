import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertNull(foo(), "message");
  }

  private Object foo() {
    return null;
  }
}
