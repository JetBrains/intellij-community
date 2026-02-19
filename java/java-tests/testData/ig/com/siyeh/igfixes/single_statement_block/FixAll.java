class X {
  static int test(int[] a) {
    if (a[0] == 0) <caret>{
      if (a[1] == 0) return 1;
      else {
        if (a[1] == 1) return 2;
      }
    } else return 3;
    return -1;
  }
}