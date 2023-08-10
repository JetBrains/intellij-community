class Test {
  void f(int i) {
    switch (i) {
      case 1:
        int <warning descr="Variable 'j' can have 'final' modifier">j</warning> = 0;
        long <warning descr="Variable 'k' can have 'final' modifier">k</warning>;
        k = 0;
        long l = 0;
        int m; m = 0;
        break;
      case 2:
        k = 2;
        l = 3;
        <error descr="Variable 'm' might not have been initialized">m</error>++;
        break;
      default:
        break;
    }
  }
}