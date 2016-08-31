class T {
  int f(boolean b) {
    int n;
    myLabel:
    {
      n = 1;
      if (b) break myLabel;
      n = 2;
    }
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}