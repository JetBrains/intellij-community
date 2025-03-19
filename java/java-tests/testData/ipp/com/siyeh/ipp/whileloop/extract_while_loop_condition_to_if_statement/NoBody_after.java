class NoBody {

  private volatile boolean flag;
  void m() {
    while (true) {
        if (!flag) break;
        System.out.println();
    }
  }
}