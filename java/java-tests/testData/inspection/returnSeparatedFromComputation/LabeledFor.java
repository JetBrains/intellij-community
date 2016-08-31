class T {
  int f(int[] a) {
    int n = -1;
    myLabel:
    for (int i = 0; i < a.length; i++) {
      if (a[0] == 0) {
        n = i;
        break myLabel;
      }
    }
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}
