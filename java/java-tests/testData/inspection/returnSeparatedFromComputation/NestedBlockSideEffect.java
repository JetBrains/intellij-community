class T {
  int f() {
    int n;
    {
      n = 1;
      System.out.println();
    }
    <warning descr="Return separated from computation of value of 'n'">return n;</warning>
  }
}