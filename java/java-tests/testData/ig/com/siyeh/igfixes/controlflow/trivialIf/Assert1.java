class Assert1 {

  void check(String s) {
    <caret>if (s == null || s.length() == 0) {
      assert false;
    }
  }
}