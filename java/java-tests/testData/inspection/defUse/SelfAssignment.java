class X {
  void foo(int x) {
    int y = y = x;

    int z = 0;
    System.out.println(z);
    z = z = x;
    System.out.println(z);
    z = (z = x);
    System.out.println(z);
    z = <warning descr="The value x assigned to 'y' is never used">y</warning> = x;
    System.out.println(z);
  }
}