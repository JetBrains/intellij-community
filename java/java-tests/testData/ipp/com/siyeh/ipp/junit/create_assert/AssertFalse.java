import junit.framework.TestCase;

public class AssertFalse extends TestCase {

  public void testOne() {
    !result()<caret>
  }

  boolean result() {
    return false;
  }
}