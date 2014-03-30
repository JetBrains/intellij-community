class Test {
  private void x(int[] <flown1111>params) {
    if (params == null) {
      params = <flown1112>new int[]{<flown11121>-1};
    }
    params[3] = <flown1113>44;
    for (int <flown11>param : <flown111>params) y(<flown1>param);
  }

  private void y(int <caret>arg) {
  }

  private void z() {
    x(<flown11111>new int[]{<flown111111>1,<flown111112>8});
  }
}
