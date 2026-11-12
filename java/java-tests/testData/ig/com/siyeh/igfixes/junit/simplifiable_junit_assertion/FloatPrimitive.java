import org.junit.*;

import static org.junit.Assert.assertTrue;

public class FloatPrimitive {

  @Test
  public void testPrimitive() {
      <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(floatValue() == 2);
  }

  float floatValue() {
    return 1.0f;
  }
}