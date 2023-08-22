class DataflowIntMixedTypes {
  void test(int[] arr) {
    switch<caret> (arr.length % 10) {
      case '\u0002': break;
      case (byte)(0x105): break;
      case (short)(0x100008): break;
    }
  }
}