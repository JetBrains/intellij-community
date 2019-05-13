class C {
  int f(boolean b, boolean c) {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    if (b) {
      if (c) {
        i = 1;
      }
      else {
        i = 2;
      }
    }
    else {
      i = 3;
    }
    return i;
  }
}