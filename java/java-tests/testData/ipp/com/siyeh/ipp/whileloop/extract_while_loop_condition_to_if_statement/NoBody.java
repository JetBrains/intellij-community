class NoBody {

  private volatile boolean flag;
  void m() {
    <caret>while (flag) System.out.println();
  }
}