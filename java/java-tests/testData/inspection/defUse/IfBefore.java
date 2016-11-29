class C {
  int f(boolean b) {
    int i;
    if (b) {
      <warning descr="The value 1 assigned to 'i' is never used">i</warning> = 1;
    }
    else {
      <warning descr="The value 2 assigned to 'i' is never used">i</warning> = 2;
    }
    i = 3;
    return i;
  }
}