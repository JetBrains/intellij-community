class C {
  int f() {
    int i;
    try {
    } finally {
      <warning descr="The value 1 assigned to 'i' is never used">i</warning> = 1;
      try {
        i = 2;
      } finally {
      }
    }
    return i;
  }
}