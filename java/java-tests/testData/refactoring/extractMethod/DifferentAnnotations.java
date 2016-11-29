class C {
  {
    @A int i = 0;
    System.out.println(i);
  }

  void f() {
    <selection>@B int j = 0;
    System.out.println(j);</selection>
  }
}
@interface A {}
@interface B {}