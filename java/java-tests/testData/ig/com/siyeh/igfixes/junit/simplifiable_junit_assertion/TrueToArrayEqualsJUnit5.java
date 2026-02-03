import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.*;
class ObjectEqualsToEquals {

  public void testObjectsEquals() {
      <warning descr="'assertTrue()' can be simplified to 'assertArrayEquals()'"><caret>assertTrue</warning>(Arrays.equals(getFoo(), getBar()), "message");
  }

  int[] getFoo() { return new int[0]; }
  int[] getBar() { return new int[0]; }
}