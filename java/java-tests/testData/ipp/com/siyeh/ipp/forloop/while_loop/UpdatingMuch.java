class UpdatingMuch {

  void m() {
    for<caret> (int i = 0, j = 0, k = 0; i < 10; i++, j++, k++) {
      System.out.println(i + j + k);
    }
  }
}