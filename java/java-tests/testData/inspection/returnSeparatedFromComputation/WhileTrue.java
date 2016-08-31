class T {
  long f() {
    long r;
    long s = System.currentTimeMillis();
    long t = s;
    while (true) {
      t = System.currentTimeMillis();
      if (t - s > 100) {
        r = t;
        break;
      }
    }
    <warning descr="Return separated from computation of value of 'r'">return r;</warning>
  }
}