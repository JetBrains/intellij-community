import junit.framework.*;

public class JUnit3TestCase extends TestCase {

  public void testOne() {
    <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(1 == 1);
  }
}