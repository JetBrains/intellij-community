import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FloatPrimitive {

  @Test
  public void testPrimitive() {
      assertEquals(2, floatValue(), 0.0f);
  }

  float floatValue() {
    return 1.0f;
  }
}