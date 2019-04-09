class C {
  {
    @A int i = 0;
    System.out.println(i);
  }

  void f() {
      NewMethodResult x = newMethod();
  }

    NewMethodResult newMethod() {
        @B int j = 0;
        System.out.println(j);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
@interface A {}
@interface B {}