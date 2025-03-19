import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertNotSame(foo(), foo(), "message");
  }

  private Object foo() {
    return null;
  }
}
