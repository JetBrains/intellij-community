// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

class Test {

  void fillByteArray() {
    byte[] plaintext = {1,2,3,4,5};
      Arrays.fill(plaintext, (byte) 0);
  }
}