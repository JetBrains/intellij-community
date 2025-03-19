class AnonymousClass {
  void foo() {
    new Object() {
      {
        System.out.println(42);
      }
    };
  }
}