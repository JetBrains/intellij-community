import junit.framework.Assert;

class MyTest {
  public void test() {
    Assert.fail();
    Assert.fa<caret>il();
  }
}