class T {
  int f(boolean a, boolean b) {
    int n = -1;
    if (a) {
      if (b) n = 1;
    }
    else n = 2;
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}