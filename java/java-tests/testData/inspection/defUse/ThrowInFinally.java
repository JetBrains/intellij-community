class C {
  int f(boolean b) {
    int i;
    try {
      <warning descr="The value 0 assigned to 'i' is never used">i</warning> = 0;
    } finally {
      if (b) throw new RuntimeException();
      i = 1;
    }
    return i;
  }
}