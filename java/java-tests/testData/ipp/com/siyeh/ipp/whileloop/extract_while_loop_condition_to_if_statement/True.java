class TrueCondition {

  private volatile boolean flag;
  void m() {
    <caret>while (true) System.out.println();
  }
}