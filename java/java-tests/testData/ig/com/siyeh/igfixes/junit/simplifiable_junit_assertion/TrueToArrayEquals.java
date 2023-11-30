import static org.junit.Assert.assertTrue;

import java.util.Arrays;

class ObjectEqualsToEquals {

  public void testObjectsEquals() {
      <warning descr="'assertTrue()' can be simplified to 'assertArrayEquals()'"><caret>assertTrue</warning>(Arrays.equals(getFoo(), getBar()));
  }

  int[] getFoo() { <error descr="Incompatible types. Found: 'java.lang.String', required: 'int[]'">return "foo";</error> }
  int[] getBar() { <error descr="Incompatible types. Found: 'java.lang.String', required: 'int[]'">return "foo";</error> }
}