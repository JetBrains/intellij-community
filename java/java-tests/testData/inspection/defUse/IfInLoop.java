class C {
  int f() {
    int i = 0;
    for (int j = 0; j < 2; j++) {
      if (j == 2) return 1;
      i = 1;
    }
    return i;
  }
}