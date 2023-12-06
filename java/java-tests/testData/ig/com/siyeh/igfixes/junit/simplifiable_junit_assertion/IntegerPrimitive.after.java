import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegerPrimitive {

  @Test
  public void testPrimitive() {
      assertEquals(0L, (int) integerValue());
  }

  Integer integerValue() {
    return 1;
  }
}