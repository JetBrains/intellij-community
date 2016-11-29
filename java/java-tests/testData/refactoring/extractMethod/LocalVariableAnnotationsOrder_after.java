class C {
  {
      newMethod();
  }

  void f() {
      newMethod();
  }

    private void newMethod() {
        @A @B int j = 0;
        System.out.println(j);
    }
}
@interface A {}
@interface B {}