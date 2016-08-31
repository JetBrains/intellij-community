class T {
  int f(boolean b) {
    int n = 0;
    myLabel:
    if (b) n = 1;
    else break myLabel;
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}