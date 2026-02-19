class Test {
  private static int getZeroSignFromDoubleBits(final long bits) {
    long bitsNonNegative = bits;

    if (bitsNonNegative < 0) {
      bitsNonNegative = 0x8000_0000_0000_0000L + bits;
    }

    if (bitsNonNegative == 0) {
      if (bits == 0) {
        return 1;
      }
      return -1;
    }
    return 0;
  }
}