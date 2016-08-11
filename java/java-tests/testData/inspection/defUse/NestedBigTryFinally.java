class C {
  int f() {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    try {
      <warning descr="The value 1 assigned to 'i' is never used">i</warning> = 1;
    }
    finally {
      <warning descr="The value 2 assigned to 'i' is never used">i</warning> = 2;
      try {
        <warning descr="The value 3 assigned to 'i' is never used">i</warning> = 3;
      }
      finally {
        <warning descr="The value 4 assigned to 'i' is never used">i</warning> = 4;
      }
      i = 5;
    }
    return i;
  }
}