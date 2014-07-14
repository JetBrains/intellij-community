class VarArgs {
  private void g() {
      f<flown111>("d",1,2,3);
  }

  void f(String value,int... <flown11>i) {
      v(value, <flown1>i);
  }

  private void v(String value, int... <caret>ints) {
  }
}
