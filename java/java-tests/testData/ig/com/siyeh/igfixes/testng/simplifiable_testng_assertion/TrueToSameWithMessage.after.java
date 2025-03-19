import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertSame(foo(), foo(), "message");
  }

  private Object foo() {
    return null;
  }
}
