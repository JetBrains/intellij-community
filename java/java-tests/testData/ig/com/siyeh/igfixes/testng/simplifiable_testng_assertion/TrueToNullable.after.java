import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertNull(foo());
  }

  private String foo() {
    return null;
  }
}
