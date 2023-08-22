class InLoopSingleLine {

  void m() {
    for(int i = 0; i < 10; i++)try {
      System.out.println(i);
    } catch (Exception <caret>ignore) { }
  }
}