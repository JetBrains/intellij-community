class m {
  int i;
  void f() {
    int r = 0;
    new Runnable() {
      public void run() {
        int k = <error descr="Variable 'r' is accessed from within inner class. Needs to be declared final.">r</error>;
        int ii = i;
      }
    };
  }
}