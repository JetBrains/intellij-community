import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
    assert<caret>Equals(true, foo(), "");
  }

  private String foo() {
    return null;
  }
}
