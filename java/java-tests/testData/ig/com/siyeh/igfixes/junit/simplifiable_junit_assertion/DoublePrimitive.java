import org.junit.*;

import static org.junit.Assert.assertTrue;

public class DoublePrimitive {

  @Test
  public void testPrimitive() {
      <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(doubleValue().equals(2.0));
  }

  Double doubleValue() {
    return 1.0;
  }
}