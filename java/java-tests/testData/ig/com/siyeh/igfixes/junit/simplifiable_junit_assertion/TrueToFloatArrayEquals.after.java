import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

class TrueToFloatArrayEquals {

  public void testArraysEquals() {
      assertArrayEquals(getFoo(), getBar(), 0.0f);
  }

  float[] getFoo() { return new float[]{1.0f}; }
  float[] getBar() { return new float[]{1.0f}; }
}