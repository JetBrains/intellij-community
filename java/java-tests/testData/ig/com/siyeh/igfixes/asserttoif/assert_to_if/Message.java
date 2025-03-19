class Message {
  void test(int n) {
    assert<caret> n > 0 : "My message";
  }
}