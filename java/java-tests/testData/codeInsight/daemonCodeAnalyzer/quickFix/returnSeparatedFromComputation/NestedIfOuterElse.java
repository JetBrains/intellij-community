class T {
  int f(boolean a, boolean b) {
    int n = -1;
    if (a) {
      if (b) n = 1;
    }
    else n = 2;
    return n;
  }
}