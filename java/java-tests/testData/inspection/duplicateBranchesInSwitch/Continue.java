class C {
  int foo(int n) {
    int s = 0;
    for (int i = 0; i < n; i++) {
      switch (i % 4) {
        case 1:
          s += i;
          continue;
        case 2:
          continue;
        case 3:
          <weak_warning descr="Duplicate branch in 'switch'">s += i;</weak_warning>
          continue;
        default:
          s += i;
      }
      s /= 2;
    }
    return s;
  }

  int foo2(int n) {
    int s = 0;
    for (int i = 0; i < n; i++) {
      switch (i % 4) {
        case 1:
          s += i;
          continue;
        case 2:
          continue;
        case 3:
          //noinspection DuplicateBranchesInSwitch
          s += i;
          continue;
        default:
          s += i;
      }
      s /= 2;
    }
    return s;
  }
}