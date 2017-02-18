class C {
  int f(boolean b) {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    try {
      if (b) throw new RuntimeException();
    } finally {
      i = 1;
    }
    return i;
  }
}