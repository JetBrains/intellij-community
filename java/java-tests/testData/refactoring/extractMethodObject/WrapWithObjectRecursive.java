class Test {
  int f<caret>oo(int i) {
      if (i == 0) return -1;
      return foo(i - 1);
  }
}