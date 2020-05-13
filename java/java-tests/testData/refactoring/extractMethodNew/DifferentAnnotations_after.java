class C {
  {
    @A int i = 0;
    System.out.println(i);
  }

  void f() {
      newMethod();
  }

    private void newMethod() {
        @B int j = 0;
        System.out.println(j);
    }
}
@interface A {}
@interface B {}