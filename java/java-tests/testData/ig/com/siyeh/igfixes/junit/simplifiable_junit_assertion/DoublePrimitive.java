import org.junit.*;

import static org.junit.Assert.assertTrue;

public class DoublePrimitive {

  @Test
  public void testPrimitive() {
      <caret>assertTrue(doubleValue().equals(2.0));
  }

  Double doubleValue() {
    return 1.0;
  }
}