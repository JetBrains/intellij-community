import org.junit.*;

import static org.junit.Assert.assertTrue;

public class IntegerPrimitive {

  @Test
  public void testPrimitive() {
    <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(integerValue().equals(0L));
  }

  Integer integerValue() {
    return 1;
  }
}