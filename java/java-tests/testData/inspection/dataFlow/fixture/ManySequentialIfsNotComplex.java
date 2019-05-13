class Some {
  void foo(int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9, int i10, int i11, int i12, int i13, Foo foo) {
    if (i1 == 0) {
      i3 = 2;
      System.out.println(foo.getBar1().length());
    }
    if (i2 == 0) System.out.println(foo.getBar2().length());
    if (i3 == 0) System.out.println(foo.getBar3().length());
    if (i4 == 0) System.out.println(foo.getBar4().length());
    if (i5 == 0) System.out.println(foo.getBar5().length());
    if (i6 == 0) System.out.println(foo.getBar6().length());
    if (i7 == 0) System.out.println(foo.getBar7().length());
    if (i8 == 0) System.out.println(foo.getBar8().length());
    if (i9 == 0) System.out.println(foo.getBar9().length());
    if (i10 == 0) System.out.println(foo.getBar10().length());
    if (i11 == 0) System.out.println(foo.getBar11().length());
    if (i12 == 0) System.out.println(foo.getBar12().length());
    if (i13 == 0) System.out.println(foo.getBar13().length());

    System.out.println(i1);
    System.out.println(i2);
    System.out.println(i3);
    System.out.println(i4);
    System.out.println(i5);
    System.out.println(i6);
    System.out.println(i7);
    System.out.println(i8);
    System.out.println(i9);
    System.out.println(i10);
    System.out.println(i11);
    System.out.println(i12);
    System.out.println(i13);

  }

}

interface Foo {
  String getBar1();
  String getBar2();
  String getBar3();
  String getBar4();
  String getBar5();
  String getBar6();
  String getBar7();
  String getBar8();
  String getBar9();
  String getBar10();
  String getBar11();
  String getBar12();
  String getBar13();
}
