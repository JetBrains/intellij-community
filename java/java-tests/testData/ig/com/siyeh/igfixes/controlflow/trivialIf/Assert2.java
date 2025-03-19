class Assert2 {

  void check(String s) {
    <caret>if (s != null) {
      assert s.length() > 0;
    }
  }
}