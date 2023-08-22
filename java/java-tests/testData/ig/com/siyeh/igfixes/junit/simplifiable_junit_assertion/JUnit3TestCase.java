import junit.framework.*;

public class JUnit3TestCase extends TestCase {

  public void testOne() {
    <caret>assertTrue(1 == 1);
  }
}