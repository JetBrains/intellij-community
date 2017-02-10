class C {
  int f(boolean b) {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    try {
      <warning descr="The value 1 assigned to 'i' is never used">i</warning> = 1;
    }
    finally {
      i = 2;
    }
    return i;
  }
}