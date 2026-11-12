import static org.junit.Assert.assertTrue;

import java.util.Arrays;

class TrueToFloatArrayEquals {

  public void testArraysEquals() {
      <warning descr="'assertTrue()' can be simplified to 'assertArrayEquals()'"><caret>assertTrue</warning>(Arrays.equals(getFoo(), getBar()));
  }

  float[] getFoo() { return new float[]{1.0f}; }
  float[] getBar() { return new float[]{1.0f}; }
}