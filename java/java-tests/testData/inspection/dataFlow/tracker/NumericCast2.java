/*
Value is always false (b == s; line#22)
  Left operand is -24 (b; line#22)
    'b' was assigned (=; line#20)
      Result of '(byte)' cast is -24 ((byte) x; line#20)
        Cast operand is 1000 (x; line#20)
          'x' was assigned to '1000' (=; line#19)
  and right operand is 1000 (s; line#22)
    's' was assigned (=; line#21)
      Result of '(short)' cast is 1000 ((short) x; line#21)
        Cast operand is 1000 (x; line#21)
          'x' was assigned to '1000' (=; line#19)
 */

import java.util.List;

class Test {
  void test() {
    int x = 1000;
    byte b = (byte) x;
    short s = (short) x;
    if (<selection>b == s</selection>) {}
  }
}