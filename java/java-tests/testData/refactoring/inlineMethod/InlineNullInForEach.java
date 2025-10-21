public class InlineNullInForEach {
  int[] data() {
    return null;
  }

  void test() {
    for (int i : d<caret>ata()) {
      System.out.println(i);
    }
  }
}