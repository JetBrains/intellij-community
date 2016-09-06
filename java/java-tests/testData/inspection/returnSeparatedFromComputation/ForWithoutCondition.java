class T {
  int f() {
    int n = -1;
    for(int i=0;; i++) {
      if (i % 127 == 0 && i % 129 == 0) {
        n = i + 1;
        break;
      }
    }
    <warning descr="Return separated from computation of value of 'n'">return</warning> n;
  }
}