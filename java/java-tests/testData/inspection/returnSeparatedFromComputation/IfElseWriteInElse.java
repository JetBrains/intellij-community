class T {
  int f(boolean b) {
    int n = 0;
    if (b) System.out.println("yes");
    else n = 2;
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}