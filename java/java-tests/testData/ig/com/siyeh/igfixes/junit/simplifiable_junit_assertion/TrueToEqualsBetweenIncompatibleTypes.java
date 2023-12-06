import org.junit.*;

import static org.junit.Assert.assertTrue;

public class TrueToEqualsBetweenIncompatibleTypes {

  @Test
  public void testOne() {
    <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>("1".equals(1));
  }
}