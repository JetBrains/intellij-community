class C {
  int f(boolean b) {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    if (b) {
      i = 1;
    }
    else {
      i = 2;
    }
    return i;
  }
}