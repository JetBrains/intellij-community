class T {
  int f() {
    int n;
    {
      n = 1;
    }
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}