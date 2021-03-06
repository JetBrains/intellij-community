public class UnboxingWithConversionCalls {
  void testConstants() {
    byte b = Integer.valueOf(1234).byteValue();
    if (<warning descr="Condition 'b == -46' is always 'true'">b == -46</warning>) {}
    byte b2 = Integer.valueOf(Short.valueOf((short)1234).intValue()).byteValue();
    if (<warning descr="Condition 'b == b2' is always 'true'">b == b2</warning>) {}
  }

  void test(Byte b) {
    if (b > 0) {
      int i = b.intValue();
      if (<warning descr="Condition 'i < 0' is always 'false'">i < 0</warning>) {}
    }
  }
}