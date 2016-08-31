class T {
  int f(int[] a, int b) {
    int n = -1;
    for (int i = 0; i < a.length; i++) {
      if (a[i] == b) {
        n = i;
        break;
      }
    }
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}