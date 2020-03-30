class C {
  {
    @B @A int i = 0;
    System.out.println(i);
  }

  void f() {
    <selection>@A @B int j = 0;
    System.out.println(j);</selection>
  }
}
@interface A {}
@interface B {}