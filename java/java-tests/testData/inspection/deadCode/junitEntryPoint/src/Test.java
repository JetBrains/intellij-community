public class Test {
  public static junit.framework.Test suite() {
    I i = new IImpl();
    A a = i.create();
    return null;
  }

  public void testSmth() {}

  public void setUp() {}

  public void tearDown(){}

  public void notATest(){}
}