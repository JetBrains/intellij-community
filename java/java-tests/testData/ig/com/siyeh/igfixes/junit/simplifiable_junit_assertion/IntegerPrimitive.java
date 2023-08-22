import org.junit.*;

import static org.junit.Assert.assertTrue;

public class IntegerPrimitive {

  @Test
  public void testPrimitive() {
    <caret>assertTrue(integerValue().equals(0L));
  }

  Integer integerValue() {
    return 1;
  }
}