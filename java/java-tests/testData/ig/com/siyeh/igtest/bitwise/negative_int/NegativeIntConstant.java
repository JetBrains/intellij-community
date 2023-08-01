public class NegativeIntConstant {
  static final int MASK = 0xF0F0F0F0;

  public static void main(String[] args) {
    long mask = <warning descr="Negative int hexadecimal constant in long context">0xFFFF_FFFF</warning>;
    long mask1 = <warning descr="Negative int hexadecimal constant in long context">0X8000_0000</warning>;
    long mask2 = 0x7FFF_FFFF;
    long mask3 = 0xFFFF_FFFFL;
    long mask4 = -1;
    long mask5 = <warning descr="Negative int hexadecimal constant in long context">MASK</warning>;
  }

  void test(long l) {
    if (l == <warning descr="Negative int hexadecimal constant in long context">0xD0D0D0D0</warning>) {}
  }

  void testAssert(int expected, long expectedLong) {
    assertEquals(0xF0F0F0F0, expected);
    assertEquals(<warning descr="Negative int hexadecimal constant in long context">0xF0F0F0F0</warning>, expectedLong);
  }

  void assertEquals(long a, long b) {

  }
}