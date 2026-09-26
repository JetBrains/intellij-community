import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
    assert<caret>True(foo().equals(foo()), "");
  }

  private String foo() {
    return null;
  }
}
