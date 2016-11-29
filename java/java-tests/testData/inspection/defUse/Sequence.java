class C {
  int f() {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    <warning descr="The value 1 assigned to 'i' is never used">i</warning> = 1;
    i = 2;
    return i;
  }
}